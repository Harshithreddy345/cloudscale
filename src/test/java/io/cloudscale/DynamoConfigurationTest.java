package io.cloudscale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import static org.assertj.core.api.Assertions.*;

class DynamoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(InMemoryJobRepository.class, DynamoMetadataConfiguration.class);
    @Test void memoryIsDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(JobRepository.class).doesNotHaveBean(DynamoDbClient.class));
    }
    @Test void dynamoReplacesMemory() {
        runner.withPropertyValues("cloudscale.metadata=dynamodb", "cloudscale.dynamodb.table=test-jobs", "cloudscale.dynamodb.region=us-east-2")
                .run(context -> {
                    assertThat(context).hasSingleBean(JobRepository.class);
                    assertThat(context.getBean(JobRepository.class)).isInstanceOf(DynamoJobRepository.class);
                });
    }
    @Test void missingRegionFailsStartup() {
        runner.withPropertyValues("cloudscale.metadata=dynamodb", "cloudscale.dynamodb.table=test-jobs", "cloudscale.dynamodb.region=")
                .run(context -> assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class));
    }
    @Test void missingTableFailsStartup() {
        runner.withPropertyValues("cloudscale.metadata=dynamodb", "cloudscale.dynamodb.region=us-east-2", "cloudscale.dynamodb.table=")
                .run(context -> assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class));
    }
}
