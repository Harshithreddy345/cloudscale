package io.cloudscale;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.DisposableBean;
import java.util.UUID;
import java.util.concurrent.*;

@Component
@ConditionalOnProperty(name = "cloudscale.dispatch", havingValue = "local", matchIfMissing = true)
public class LocalJobDispatcher implements JobDispatcher, DisposableBean {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.AbortPolicy());
    private final JobWorker worker;
    public LocalJobDispatcher(JobWorker worker) { this.worker = worker; }
    public void dispatch(UUID id) { executor.execute(() -> worker.process(id)); }
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
    }
}
