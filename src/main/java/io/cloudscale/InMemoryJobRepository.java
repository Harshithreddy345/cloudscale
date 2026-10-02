package io.cloudscale;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Repository
@ConditionalOnProperty(name = "cloudscale.metadata", havingValue = "memory", matchIfMissing = true)
public class InMemoryJobRepository implements JobRepository {
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();
    public void create(Job job) {
        if (jobs.putIfAbsent(job.id(), job) != null) throw new IllegalStateException("Duplicate job ID");
    }
    public Optional<Job> find(UUID id) { return Optional.ofNullable(jobs.get(id)); }
    public List<Job> list() { return jobs.values().stream().sorted(Comparator.comparing(Job::createdAt).reversed()).toList(); }
    public boolean transition(UUID id, Job.Status expected, Job.Status next, String error) {
        var changed = new AtomicBoolean();
        jobs.computeIfPresent(id, (key, job) -> {
            if (job.status() != expected) return job;
            changed.set(true);
            return job.transition(next, error);
        });
        return changed.get();
    }
}
