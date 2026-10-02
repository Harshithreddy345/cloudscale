package io.cloudscale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
class StorageErrorApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean JobRepository jobs;
    @MockitoBean FileStore files;
    @MockitoBean JobDispatcher dispatcher;

    @Test void failedUploadReturns503WithoutCreatingOrDispatchingJob() throws Exception {
        doThrow(new IOException("private internal bucket detail"))
                .when(files).saveInput(any(UUID.class), any(InputStream.class));
        mvc.perform(multipart("/jobs").file(new MockMultipartFile("file", "sales.csv", "text/csv", new byte[]{1})))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("detail").value("File storage is unavailable. Try again later."));
        verifyNoInteractions(jobs, dispatcher);
    }

    @Test void temporaryDownloadFailureReturns503() throws Exception {
        var id = UUID.randomUUID();
        var now = Instant.now();
        when(jobs.find(id)).thenReturn(Optional.of(new Job(id, "sales.csv", Job.Status.COMPLETED, now, now, now, null)));
        when(files.readResult(id)).thenThrow(new IOException("private internal detail"));
        mvc.perform(get("/jobs/{id}/result", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("detail").value("File storage is unavailable. Try again later."));
    }
}
