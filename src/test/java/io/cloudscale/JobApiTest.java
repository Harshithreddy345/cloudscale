package io.cloudscale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JobApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JobRepository jobs;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) throws Exception {
        var dir = Files.createTempDirectory("cloudscale-test-");
        registry.add("cloudscale.storage-root", dir::toString);
    }
    private UUID upload(byte[] contents) throws Exception {
        var response = mvc.perform(multipart("/jobs").file(new MockMultipartFile("file", "sales.csv", "text/csv", contents)))
                .andExpect(status().isAccepted()).andExpect(header().exists("Location")).andReturn();
        return UUID.fromString(json.readTree(response.getResponse().getContentAsString()).get("id").asText());
    }
    private Job await(UUID id) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            var job = jobs.find(id).orElseThrow();
            if (job.status() == Job.Status.COMPLETED || job.status() == Job.Status.FAILED) return job;
            Thread.sleep(10);
        }
        throw new AssertionError("Job did not finish");
    }
    @Test void uploadPollAndDownloadReport() throws Exception {
        var id = upload(Files.readAllBytes(java.nio.file.Path.of("sample-data/sales.csv")));
        assertThat(await(id).status()).isEqualTo(Job.Status.COMPLETED);
        mvc.perform(get("/jobs/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("status").value("COMPLETED"));
        var report = mvc.perform(get("/jobs/{id}/result", id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(report).contains("valid_rows,all,3", "invalid_rows,all,1", "revenue,all,1179.97", "revenue_by_region,Texas,279.98");
        mvc.perform(get("/jobs")).andExpect(status().isOk());
    }
    @Test void malformedHeadersFailJob() throws Exception {
        var id = upload("wrong,header\n1,2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(await(id).status()).isEqualTo(Job.Status.FAILED);
        mvc.perform(get("/jobs/{id}/result", id)).andExpect(status().isConflict());
    }
    @Test void emptyUploadAndMissingJob() throws Exception {
        mvc.perform(multipart("/jobs").file(new MockMultipartFile("file", new byte[0]))).andExpect(status().isBadRequest());
        mvc.perform(get("/jobs/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/jobs/not-a-uuid")).andExpect(status().isBadRequest());
    }
    @Test void unfinishedResultAndDuplicateClaim() throws Exception {
        var id = UUID.randomUUID();
        jobs.create(new Job(id,"sample.csv",Job.Status.QUEUED,java.time.Instant.now(),null,null,null));
        mvc.perform(get("/jobs/{id}/result", id)).andExpect(status().isConflict());
        assertThat(jobs.transition(id,Job.Status.QUEUED,Job.Status.PROCESSING,null)).isTrue();
        assertThat(jobs.transition(id,Job.Status.QUEUED,Job.Status.PROCESSING,null)).isFalse();
    }
}
