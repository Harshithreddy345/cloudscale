package io.cloudscale;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamoJobRepositoryTest {
    private final DynamoDbClient client = mock(DynamoDbClient.class);
    private final DynamoJobRepository repository = new DynamoJobRepository(client, "cloudscale-jobs");
    private Job queued() { return new Job(UUID.randomUUID(), "sales.csv", Job.Status.QUEUED, Instant.now(), null, null, null); }

    @Test void preservesAllMetadataAcrossRepositoryInstances() {
        var job = queued().transition(Job.Status.PROCESSING, null).transition(Job.Status.FAILED, "bad CSV");
        repository.create(job);
        var request = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(client).putItem(request.capture());
        assertThat(request.getValue().conditionExpression()).isEqualTo("attribute_not_exists(jobId)");
        assertThat(request.getValue().item().get("inputS3Key").s()).isEqualTo("uploads/" + job.id() + ".csv");
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(request.getValue().item()).build());
        assertThat(new DynamoJobRepository(client, "cloudscale-jobs").find(job.id())).contains(job);
        var read = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(client).getItem(read.capture());
        assertThat(read.getValue().consistentRead()).isTrue();
    }
    @Test void rejectsDuplicateIdsWithoutOverwriting() {
        when(client.putItem(any(PutItemRequest.class))).thenThrow(ConditionalCheckFailedException.builder().message("duplicate").build());
        assertThatThrownBy(() -> repository.create(queued())).isInstanceOf(IllegalStateException.class);
    }
    @Test void claimUsesAtomicExpectedStatusAndConflictsReturnFalse() {
        var id = UUID.randomUUID();
        when(client.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build())
                .thenThrow(ConditionalCheckFailedException.builder().build());
        assertThat(repository.transition(id, Job.Status.QUEUED, Job.Status.PROCESSING, null)).isTrue();
        assertThat(repository.transition(id, Job.Status.QUEUED, Job.Status.PROCESSING, null)).isFalse();
        var requests = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(client, times(2)).updateItem(requests.capture());
        var request = requests.getValue();
        assertThat(request.conditionExpression()).isEqualTo("attribute_exists(jobId) AND #status = :expected");
        assertThat(request.expressionAttributeValues().get(":expected").s()).isEqualTo("QUEUED");
        assertThat(request.updateExpression()).contains("startedAt = :now", "REMOVE #error");
        assertThat(request.expressionAttributeNames()).containsEntry("#error", "error");
    }
    @Test void listsEveryScanPageAndSortsNewestFirst() {
        var old = new Job(UUID.randomUUID(), "old.csv", Job.Status.QUEUED, Instant.parse("2026-01-01T00:00:00Z"), null, null, null);
        var recent = queued();
        var cursor = Map.of("jobId", AttributeValue.builder().s(old.id().toString()).build());
        when(client.scan(any(ScanRequest.class))).thenReturn(
                ScanResponse.builder().items(DynamoJobRepository.encode(old)).lastEvaluatedKey(cursor).build(),
                ScanResponse.builder().items(DynamoJobRepository.encode(recent)).build());
        assertThat(repository.list()).containsExactly(recent, old);
        var requests = ArgumentCaptor.forClass(ScanRequest.class);
        verify(client, times(2)).scan(requests.capture());
        assertThat(requests.getAllValues().get(0).hasExclusiveStartKey()).isFalse();
        assertThat(requests.getAllValues().get(1).exclusiveStartKey()).isEqualTo(cursor);
    }
    @Test void missingJobIsEmptyAndServiceOutageIsNotAConflict() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        assertThat(repository.find(UUID.randomUUID())).isEmpty();
        when(client.updateItem(any(UpdateItemRequest.class))).thenThrow(DynamoDbException.builder().message("private AWS details").build());
        assertThatThrownBy(() -> repository.transition(UUID.randomUUID(), Job.Status.QUEUED, Job.Status.PROCESSING, null))
                .isInstanceOf(MetadataUnavailableException.class).hasMessage("Job metadata is unavailable");
    }
    @Test void terminalStatesCannotTransitionAndTableIsRequired() {
        assertThatThrownBy(() -> repository.transition(UUID.randomUUID(), Job.Status.COMPLETED, Job.Status.PROCESSING, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(client);
        assertThatThrownBy(() -> new DynamoJobRepository(client, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
