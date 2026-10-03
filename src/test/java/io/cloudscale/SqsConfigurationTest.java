package io.cloudscale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqsConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SqsConfiguration.class)
            .withBean(JobWorker.class, () -> mock(JobWorker.class));
    private ApplicationContextRunner sqs() {
        return runner.withPropertyValues("cloudscale.dispatch=sqs", "cloudscale.storage=s3", "cloudscale.metadata=dynamodb",
                "cloudscale.sqs.region=us-east-2", "cloudscale.sqs.queue-url=https://sqs.us-east-2.amazonaws.com/123456789012/test");
    }
    @Test void apiPublishesWithoutStartingConsumer() {
        sqs().run(context -> assertThat(context).hasSingleBean(JobDispatcher.class).doesNotHaveBean(SqsJobConsumer.class));
    }
    @Test void requiresDurableStorageAndMetadata() {
        sqs().withPropertyValues("cloudscale.metadata=memory").run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class));
    }
    @Test void missingQueueFailsStartup() {
        sqs().withPropertyValues("cloudscale.sqs.queue-url=").run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class));
    }
    @Test void missingRegionFailsStartup() {
        sqs().withPropertyValues("cloudscale.sqs.region=").run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class));
    }
}
