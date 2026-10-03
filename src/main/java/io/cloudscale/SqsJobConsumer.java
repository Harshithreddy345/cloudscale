package io.cloudscale;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

/** One message at a time; unresolved work is left for redelivery and the configured DLQ. */
public class SqsJobConsumer implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(SqsJobConsumer.class);
    private final SqsClient client;
    private final String queueUrl;
    private final JobWorker worker;
    private volatile boolean running;
    private Thread thread;
    public SqsJobConsumer(SqsClient client, String queueUrl, JobWorker worker) {
        this.client = client; this.queueUrl = queueUrl; this.worker = worker;
    }
    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "cloudscale-sqs-worker");
        thread.start();
    }
    private void loop() {
        while (running) {
            try { pollOnce(); }
            catch (Exception e) {
                if (running) log.warn("SQS poll failed; will retry", e);
                try { Thread.sleep(1000); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
    }
    void pollOnce() {
        var response = client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl)
                .maxNumberOfMessages(1).waitTimeSeconds(5).build());
        for (var message : response.messages()) handle(message);
    }
    void handle(Message message) {
        final UUID id;
        try { if (message.body() == null) throw new IllegalArgumentException("Missing job id"); id = UUID.fromString(message.body()); }
        catch (IllegalArgumentException e) { log.warn("Malformed SQS message retained for redrive; messageId={}", message.messageId()); return; }
        // No acknowledgement unless a durable terminal state was confirmed. No message bodies in logs.
        if (worker.process(id)) {
            client.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
            log.info("jobId={} SQS message acknowledged", id);
        }
    }
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(25000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
    public boolean isRunning() { return running; }
}

