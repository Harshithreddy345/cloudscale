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
        try {
            if (!jobs.transition(id, Job.Status.QUEUED, Job.Status.PROCESSING, null)) return;
        } catch (MetadataUnavailableException e) {
            log.warn("jobId={} claim could not be confirmed; reconciliation required", id, e);
            return;
        }
        try (var input = files.openInput(id)) {
            files.saveResult(id, processor.process(input));
        } catch (Exception e) {
            log.warn("jobId={} processing failed", id, e);
            try { jobs.transition(id, Job.Status.PROCESSING, Job.Status.FAILED, "Job processing failed; inspect server logs for details"); }
            catch (MetadataUnavailableException failure) { log.warn("jobId={} failure status could not be confirmed", id, failure); }
            return;
        }
        // Result storage and metadata are separate writes. Do not label a saved report as a processing failure.
        try {
            if (jobs.transition(id, Job.Status.PROCESSING, Job.Status.COMPLETED, null)) log.info("jobId={} status=COMPLETED", id);
            else log.warn("jobId={} completion rejected; reconciliation required", id);
        } catch (MetadataUnavailableException e) { log.warn("jobId={} completion could not be confirmed; reconciliation required", id, e); }
    }
}
