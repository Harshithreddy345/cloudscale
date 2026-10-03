package io.cloudscale;
import java.util.UUID;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class SqsJobDispatcher implements JobDispatcher {
    private final SqsClient client;
    private final String queueUrl;
    public SqsJobDispatcher(SqsClient client, String queueUrl) { this.client = client; this.queueUrl = queueUrl; }
    public void dispatch(UUID id) {
        try { client.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(id.toString()).build()); }
        catch (SdkException e) { throw new QueueUnavailableException(e); }
    }
}
