package io.cloudscale;

public class MetadataUnavailableException extends RuntimeException {
    public MetadataUnavailableException(Throwable cause) { super("Job metadata is unavailable", cause); }
}
