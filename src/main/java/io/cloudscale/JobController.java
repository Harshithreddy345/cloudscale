package io.cloudscale;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

@RestController
@ConditionalOnProperty(name = "cloudscale.api.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/jobs")
public class JobController {
    private final JobRepository jobs;
    private final FileStore files;
    private final JobDispatcher dispatcher;
    public JobController(JobRepository jobs, FileStore files, JobDispatcher dispatcher) { this.jobs = jobs; this.files = files; this.dispatcher = dispatcher; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Job> create(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        var id = UUID.randomUUID();
        var name = Objects.toString(file.getOriginalFilename(), "upload.csv").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        var job = new Job(id, name, Job.Status.QUEUED, Instant.now(), null, null, null);
        try (var input = file.getInputStream()) { files.saveInput(id, input); }
        jobs.create(job);
        try { dispatcher.dispatch(id); }
        catch (RejectedExecutionException e) {
            jobs.transition(id, Job.Status.QUEUED, Job.Status.FAILED, "Local processing queue is full");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Queue full; retry later");
        }
        return ResponseEntity.accepted().location(URI.create("/jobs/" + id)).body(job);
    }
    @GetMapping public List<Job> list() { return jobs.list(); }
    @GetMapping("/{id}") public Job find(@PathVariable UUID id) {
        return jobs.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }
    @GetMapping("/{id}/result") public ResponseEntity<byte[]> result(@PathVariable UUID id) throws IOException {
        if (find(id).status() != Job.Status.COMPLETED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Result not available");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + id + ".csv").body(files.readResult(id));
    }
}
