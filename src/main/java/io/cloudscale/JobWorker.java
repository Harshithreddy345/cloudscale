package io.cloudscale;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Component
public class JobWorker {
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private final JobRepository jobs;
    private final FileStore files;
    private final SalesProcessor processor;
    public JobWorker(JobRepository jobs, FileStore files, SalesProcessor processor) { this.jobs = jobs; this.files = files; this.processor = processor; }
    public void process(UUID id) {
        if (!jobs.transition(id, Job.Status.QUEUED, Job.Status.PROCESSING, null)) return;
        try (var input = files.openInput(id)) {
            files.saveResult(id, processor.process(input));
            jobs.transition(id, Job.Status.PROCESSING, Job.Status.COMPLETED, null);
            log.info("jobId={} status=COMPLETED", id);
        } catch (Exception e) {
            log.warn("jobId={} processing failed", id, e);
            jobs.transition(id, Job.Status.PROCESSING, Job.Status.FAILED, "Unable to process CSV; check headers and format");
        }
    }
}
