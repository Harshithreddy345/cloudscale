package io.cloudscale;

import java.util.UUID;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MetadataFailureTest {
    @Test void apiHidesDatabaseDetailsAndReturns503() throws Exception {
        var jobs = mock(JobRepository.class);
        when(jobs.list()).thenThrow(new MetadataUnavailableException(new RuntimeException("private details")));
        var mvc = MockMvcBuilders.standaloneSetup(new JobController(jobs, mock(FileStore.class), mock(JobDispatcher.class)))
                .setControllerAdvice(new StorageErrorHandler()).build();
        mvc.perform(get("/jobs")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Job metadata is unavailable. Try again later."));
    }
    @Test void savedResultIsNotMarkedFailedWhenCompletionWriteIsUnavailable() throws Exception {
        var id = UUID.randomUUID();
        var jobs = mock(JobRepository.class);
        var files = mock(FileStore.class);
        var processor = mock(SalesProcessor.class);
        when(jobs.transition(id, Job.Status.QUEUED, Job.Status.PROCESSING, null)).thenReturn(true);
        when(files.openInput(id)).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(processor.process(any())).thenReturn(new byte[0]);
        when(jobs.transition(id, Job.Status.PROCESSING, Job.Status.COMPLETED, null)).thenThrow(new MetadataUnavailableException(new RuntimeException()));
        new JobWorker(jobs, files, processor).process(id);
        verify(files).saveResult(eq(id), any());
        verify(jobs, never()).transition(eq(id), eq(Job.Status.PROCESSING), eq(Job.Status.FAILED), any());
    }
}
