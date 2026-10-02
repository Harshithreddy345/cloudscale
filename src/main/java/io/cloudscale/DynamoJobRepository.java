package io.cloudscale;

import java.time.Instant;
import java.util.*;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** Durable metadata; condition checks make each status claim atomic across workers. */
public class DynamoJobRepository implements JobRepository {
    private final DynamoDbClient client;
    private final String table;
    public DynamoJobRepository(DynamoDbClient client, String table) {
        if (table == null || table.isBlank()) throw new IllegalArgumentException("cloudscale.dynamodb.table is required");
        this.client = client;
        this.table = table.trim();
    }
    public void create(Job job) {
        try {
            client.putItem(PutItemRequest.builder().tableName(table).item(encode(job))
                    .conditionExpression("attribute_not_exists(jobId)").build());
        } catch (ConditionalCheckFailedException e) { throw new IllegalStateException("Duplicate job ID", e); }
        catch (SdkException e) { throw new MetadataUnavailableException(e); }
    }
    public Optional<Job> find(UUID id) {
        try {
            var item = client.getItem(GetItemRequest.builder().tableName(table).key(key(id)).consistentRead(true).build()).item();
            return item.isEmpty() ? Optional.empty() : Optional.of(decode(item));
        } catch (SdkException e) { throw new MetadataUnavailableException(e); }
    }
    public List<Job> list() {
        // Demonstration endpoint: scan every page. Replace with indexed, paginated queries before scale.
        try {
            var jobs = new ArrayList<Job>();
            Map<String, AttributeValue> cursor = Map.of();
            do {
                var request = ScanRequest.builder().tableName(table).consistentRead(true);
                if (cursor != null && !cursor.isEmpty()) request.exclusiveStartKey(cursor);
                var page = client.scan(request.build());
                page.items().forEach(item -> jobs.add(decode(item)));
                cursor = page.lastEvaluatedKey();
            } while (cursor != null && !cursor.isEmpty());
            return jobs.stream().sorted(Comparator.comparing(Job::createdAt).reversed()).toList();
        } catch (SdkException e) { throw new MetadataUnavailableException(e); }
    }
    public boolean transition(UUID id, Job.Status expected, Job.Status next, String error) {
        boolean valid = expected == Job.Status.QUEUED && (next == Job.Status.PROCESSING || next == Job.Status.FAILED)
                || expected == Job.Status.PROCESSING && (next == Job.Status.COMPLETED || next == Job.Status.FAILED);
        if (!valid) throw new IllegalStateException("Invalid job transition");
        var names = Map.of("#status", "status", "#error", "error");
        var values = new HashMap<String, AttributeValue>();
        values.put(":expected", text(expected.name()));
        values.put(":next", text(next.name()));
        values.put(":now", text(Instant.now().toString()));
        String update = "SET #status = :next, " + (next == Job.Status.PROCESSING ? "startedAt" : "completedAt") + " = :now";
        if (error == null) update += " REMOVE #error";
        else { update += ", #error = :error"; values.put(":error", text(error)); }
        try {
            client.updateItem(UpdateItemRequest.builder().tableName(table).key(key(id))
                    .conditionExpression("attribute_exists(jobId) AND #status = :expected")
                    .updateExpression(update).expressionAttributeNames(names).expressionAttributeValues(values).build());
            return true;
        } catch (ConditionalCheckFailedException e) { return false; }
        catch (SdkException e) { throw new MetadataUnavailableException(e); }
    }
    private static Map<String, AttributeValue> key(UUID id) { return Map.of("jobId", text(id.toString())); }
    private static AttributeValue text(String value) { return AttributeValue.builder().s(value).build(); }
    static Map<String, AttributeValue> encode(Job job) {
        var item = new HashMap<String, AttributeValue>(key(job.id()));
        item.put("fileName", text(job.fileName()));
        item.put("status", text(job.status().name()));
        item.put("createdAt", text(job.createdAt().toString()));
        item.put("inputS3Key", text("uploads/" + job.id() + ".csv"));
        item.put("resultS3Key", text("results/" + job.id() + ".csv"));
        if (job.startedAt() != null) item.put("startedAt", text(job.startedAt().toString()));
        if (job.completedAt() != null) item.put("completedAt", text(job.completedAt().toString()));
        if (job.error() != null) item.put("error", text(job.error()));
        return item;
    }
    static Job decode(Map<String, AttributeValue> item) {
        return new Job(UUID.fromString(item.get("jobId").s()), item.get("fileName").s(), Job.Status.valueOf(item.get("status").s()),
                Instant.parse(item.get("createdAt").s()), instant(item, "startedAt"), instant(item, "completedAt"),
                item.containsKey("error") ? item.get("error").s() : null);
    }
    private static Instant instant(Map<String, AttributeValue> item, String name) {
        return item.containsKey(name) ? Instant.parse(item.get(name).s()) : null;
    }
}
