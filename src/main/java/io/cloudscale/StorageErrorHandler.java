package io.cloudscale;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class StorageErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(StorageErrorHandler.class);
    @ExceptionHandler(QueueUnavailableException.class)
    public ProblemDetail queueUnavailable(QueueUnavailableException failure) {
        log.warn("Job dispatch could not be confirmed", failure);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Job queue is unavailable. Submission may have been accepted; inspect the job listing before retrying.");
    }
    @ExceptionHandler(MetadataUnavailableException.class)
    public ProblemDetail metadataUnavailable(MetadataUnavailableException failure) {
        log.warn("Job metadata request failed", failure);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Job metadata is unavailable. Try again later.");
    }
    @ExceptionHandler(IOException.class)
    public ProblemDetail unavailable(IOException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "File storage is unavailable. Try again later.");
    }
}
