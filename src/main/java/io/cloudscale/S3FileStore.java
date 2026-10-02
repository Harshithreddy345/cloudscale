package io.cloudscale;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Object storage adapter. The caller owns input/download streams and must close them. */
public class S3FileStore implements FileStore {
    private final S3Client client;
    private final String bucket;

    public S3FileStore(S3Client client, String bucket) {
        if (bucket == null || bucket.isBlank())
            throw new IllegalArgumentException("cloudscale.s3.bucket is required for S3 storage");
        this.client = client;
        this.bucket = bucket.trim();
    }

    @Override
    public void saveInput(UUID id, InputStream input) throws IOException {
        Path staged = Files.createTempFile("cloudscale-upload-", ".csv");
        try {
            // A replayable file gives the SDK an exact length and allows retries without buffering the upload in heap.
            Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING);
            client.putObject(putRequest("uploads/" + id + ".csv"), RequestBody.fromFile(staged));
        } catch (SdkException e) {
            throw new IOException("Unable to upload input to object storage", e);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    @Override
    public InputStream openInput(UUID id) throws IOException {
        try {
            return client.getObject(getRequest("uploads/" + id + ".csv"));
        } catch (SdkException e) {
            throw new IOException("Unable to download input from object storage", e);
        }
    }

    @Override
    public void saveResult(UUID id, byte[] result) throws IOException {
        try {
            client.putObject(putRequest("results/" + id + ".csv"), RequestBody.fromBytes(result));
        } catch (SdkException e) {
            throw new IOException("Unable to upload report to object storage", e);
        }
    }

    @Override
    public byte[] readResult(UUID id) throws IOException {
        // Closing the SDK response stream releases the underlying HTTP connection.
        try (var response = client.getObject(getRequest("results/" + id + ".csv"))) {
            return response.readAllBytes();
        } catch (SdkException e) {
            throw new IOException("Unable to download report from object storage", e);
        }
    }

    private PutObjectRequest putRequest(String key) {
        return PutObjectRequest.builder().bucket(bucket).key(key).contentType("text/csv; charset=utf-8").build();
    }

    private GetObjectRequest getRequest(String key) {
        return GetObjectRequest.builder().bucket(bucket).key(key).build();
    }
}
