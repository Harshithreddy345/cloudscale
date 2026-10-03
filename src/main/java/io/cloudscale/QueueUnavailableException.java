package io.cloudscale;
public class QueueUnavailableException extends RuntimeException {
    public QueueUnavailableException(Throwable cause) { super("Job queue is unavailable", cause); }
}
