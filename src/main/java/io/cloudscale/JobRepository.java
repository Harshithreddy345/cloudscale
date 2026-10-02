package io.cloudscale;
import java.util.*;
public interface JobRepository {
    void create(Job job);
    Optional<Job> find(UUID id);
    List<Job> list();
    boolean transition(UUID id, Job.Status expected, Job.Status next, String error);
}
