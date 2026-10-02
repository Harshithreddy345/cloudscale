package io.cloudscale;

import java.time.Instant;
import java.util.UUID;

public record Job(UUID id, String fileName, Status status, Instant createdAt,
                  Instant startedAt, Instant completedAt, String error) {
    public enum Status { QUEUED, PROCESSING, COMPLETED, FAILED }
    public Job transition(Status next, String failure) {
        boolean valid = status == Status.QUEUED && (next == Status.PROCESSING || next == Status.FAILED)
                || status == Status.PROCESSING && (next == Status.COMPLETED || next == Status.FAILED);
        if (!valid) throw new IllegalStateException("Invalid job transition");
        return new Job(id, fileName, next, createdAt, next == Status.PROCESSING ? Instant.now() : startedAt,
                next == Status.COMPLETED || next == Status.FAILED ? Instant.now() : null, failure);
    }
}
