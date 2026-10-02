package io.cloudscale;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3FileStoreTest {
    @Test void uploadUsesReplayableBodyAndUuidKey() throws Exception {
        var client = mock(S3Client.class);
        var id = UUID.randomUUID();
        var input = "quoted,CSV\n1,2\n".getBytes(StandardCharsets.UTF_8);
        var source = spy(new ByteArrayInputStream(input));
        final RequestBody[] savedBody = new RequestBody[1];
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
            PutObjectRequest request = call.getArgument(0);
            RequestBody body = call.getArgument(1);
            savedBody[0] = body;
            assertThat(request.bucket()).isEqualTo("test-bucket");
            assertThat(request.key()).isEqualTo("uploads/" + id + ".csv");
            assertThat(request.contentType()).isEqualTo("text/csv; charset=utf-8");
            assertThat(body.optionalContentLength()).contains((long) input.length);
            // A retry can read exactly the same content twice.
            for (int attempt = 0; attempt < 2; attempt++)
                try (var stream = body.contentStreamProvider().newStream()) { assertThat(stream.readAllBytes()).isEqualTo(input); }
            return PutObjectResponse.builder().build();
        });
        new S3FileStore(client, "test-bucket").saveInput(id, source);
        verify(source, never()).close();
        assertThatThrownBy(() -> savedBody[0].contentStreamProvider().newStream()).isInstanceOf(RuntimeException.class);
    }

    @Test void failedUploadCleansStagingAndPreservesCause() throws Exception {
        var client = mock(S3Client.class);
        var denied = S3Exception.builder().statusCode(403).message("Access denied").build();
        final RequestBody[] body = new RequestBody[1];
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
            body[0] = call.getArgument(1);
            throw denied;
        });
        assertThatThrownBy(() -> new S3FileStore(client, "test-bucket").saveInput(UUID.randomUUID(), new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(IOException.class).hasCause(denied);
        assertThatThrownBy(() -> body[0].contentStreamProvider().newStream()).isInstanceOf(RuntimeException.class);
    }

    @Test void downloadReportClosesResponseStream() throws Exception {
        var client = mock(S3Client.class);
        var id = UUID.randomUUID();
        var source = spy(new ByteArrayInputStream("report".getBytes(StandardCharsets.UTF_8)));
        var response = new ResponseInputStream<>(GetObjectResponse.builder().build(), source);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);
        assertThat(new S3FileStore(client, "test-bucket").readResult(id)).isEqualTo("report".getBytes(StandardCharsets.UTF_8));
        verify(client).getObject(GetObjectRequest.builder().bucket("test-bucket").key("results/" + id + ".csv").build());
        verify(source).close();
    }

    @Test void storageFailureMarksWorkerFailedInsteadOfCompleted() {
        var client = mock(S3Client.class);
        var id = UUID.randomUUID();
        when(client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Missing input").build());
        var jobs = new InMemoryJobRepository();
        jobs.create(new Job(id, "sales.csv", Job.Status.QUEUED, java.time.Instant.now(), null, null, null));
        new JobWorker(jobs, new S3FileStore(client, "test-bucket"), new SalesProcessor()).process(id);
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(Job.Status.FAILED);
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test void rejectsMissingBucket() {
        assertThatThrownBy(() -> new S3FileStore(mock(S3Client.class), " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bucket");
    }
}
