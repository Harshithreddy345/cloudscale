package io.cloudscale;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class StorageErrorHandler {
    @ExceptionHandler(IOException.class)
    public ProblemDetail unavailable(IOException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "File storage is unavailable. Try again later.");
    }
}
