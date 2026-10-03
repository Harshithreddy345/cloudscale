package io.cloudscale;

import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SqsIntegrationTest {
    private final SqsClient client = mock(SqsClient.class);
    private final JobWorker worker = mock(JobWorker.class);
    private final String url = "https://sqs.us-east-2.amazonaws.com/123456789012/test";
    private Message message(UUID id) { return Message.builder().messageId("message").body(id.toString()).receiptHandle("receipt").build(); }

    @Test void dispatcherSendsOnlyJobId() {
        var id = UUID.randomUUID();
        new SqsJobDispatcher(client, url).dispatch(id);
        var request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(client).sendMessage(request.capture());
        assertThat(request.getValue().messageBody()).isEqualTo(id.toString());
        assertThat(request.getValue().queueUrl()).isEqualTo(url);
    }
    @Test void acknowledgesOnlyConfirmedTerminalJobs() {
        var id = UUID.randomUUID();
        when(worker.process(id)).thenReturn(true);
        new SqsJobConsumer(client, url, worker).handle(message(id));
        var request = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(client).deleteMessage(request.capture());
        assertThat(request.getValue().receiptHandle()).isEqualTo("receipt");
    }
    @Test void unresolvedWorkIsNotDeleted() {
        new SqsJobConsumer(client, url, worker).handle(message(UUID.randomUUID()));
        verify(client, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void malformedMessagesAreRetainedForDlq() {
        new SqsJobConsumer(client, url, worker).handle(Message.builder().body("not-a-job-id").messageId("malformed").build());
        verifyNoInteractions(worker, client);
    }
    @Test void databaseOutageDoesNotDeleteTheMessage() {
        var id = UUID.randomUUID();
        when(worker.process(id)).thenThrow(new MetadataUnavailableException(new RuntimeException()));
        assertThatThrownBy(() -> new SqsJobConsumer(client, url, worker).handle(message(id))).isInstanceOf(MetadataUnavailableException.class);
        verify(client, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void completedDuplicateDoesNotReadOrRewriteTheReport() {
        var jobs = mock(JobRepository.class);
        var files = mock(FileStore.class);
        var id = UUID.randomUUID();
        var completed = new Job(id, "sales.csv", Job.Status.COMPLETED, Instant.now(), Instant.now(), Instant.now(), null);
        when(jobs.find(id)).thenReturn(java.util.Optional.of(completed));
        assertThat(new JobWorker(jobs, files, mock(SalesProcessor.class)).process(id)).isTrue();
        verifyNoInteractions(files);
    }
    @Test void processingDuplicateIsNotAcknowledged() {
        var jobs = mock(JobRepository.class);
        var id = UUID.randomUUID();
        when(jobs.find(id)).thenReturn(java.util.Optional.of(new Job(id, "sales.csv", Job.Status.PROCESSING, Instant.now(), Instant.now(), null, null)));
        assertThat(new JobWorker(jobs, mock(FileStore.class), mock(SalesProcessor.class)).process(id)).isFalse();
    }
    @Test void failedPublishReturns503AndPreservesQueuedRecord() throws Exception {
        var jobs = new InMemoryJobRepository();
        when(client.sendMessage(any(SendMessageRequest.class))).thenThrow(SqsException.builder().message("private details").build());
        var mvc = MockMvcBuilders.standaloneSetup(new JobController(jobs, mock(FileStore.class), new SqsJobDispatcher(client, url)))
                .setControllerAdvice(new StorageErrorHandler()).build();
        mvc.perform(multipart("/jobs").file(new MockMultipartFile("file", "sales.csv", "text/csv", new byte[]{1})))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value(
                        "Job queue is unavailable. Submission may have been accepted; inspect the job listing before retrying."));
        assertThat(jobs.list()).hasSize(1);
        assertThat(jobs.list().getFirst().status()).isEqualTo(Job.Status.QUEUED);
    }
    @Test void longPollRequestsOneMessage() {
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().build());
        new SqsJobConsumer(client, url, worker).pollOnce();
        var request = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(client).receiveMessage(request.capture());
        assertThat(request.getValue().waitTimeSeconds()).isEqualTo(5);
        assertThat(request.getValue().maxNumberOfMessages()).isEqualTo(1);
    }
}
