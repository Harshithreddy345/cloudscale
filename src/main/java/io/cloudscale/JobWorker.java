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
    public boolean process(UUID id) {
        try {
            if (!jobs.transition(id, Job.Status.QUEUED, Job.Status.PROCESSING, null)) return terminal(id);
        } catch (MetadataUnavailableException e) {
            log.warn("jobId={} claim could not be confirmed; reconciliation required", id, e);
            return false;
        }
        try (var input = files.openInput(id)) {
            files.saveResult(id, processor.process(input));
        } catch (Exception e) {
            log.warn("jobId={} processing failed", id, e);
            try { return jobs.transition(id, Job.Status.PROCESSING, Job.Status.FAILED, "Job processing failed; inspect server logs for details") || terminal(id); }
            catch (MetadataUnavailableException failure) { log.warn("jobId={} failure status could not be confirmed", id, failure); return false; }
        }
        // Result storage and metadata are separate writes. Do not label a saved report as a processing failure.
        try {
            if (jobs.transition(id, Job.Status.PROCESSING, Job.Status.COMPLETED, null)) {
                log.info("jobId={} status=COMPLETED", id);
                return true;
            }
            else log.warn("jobId={} completion rejected; reconciliation required", id);
            return terminal(id);
        } catch (MetadataUnavailableException e) { log.warn("jobId={} completion could not be confirmed; reconciliation required", id, e); return false; }
    }
    private boolean terminal(UUID id) {
        return jobs.find(id).map(job -> job.status() == Job.Status.COMPLETED || job.status() == Job.Status.FAILED).orElse(false);
    }
}
