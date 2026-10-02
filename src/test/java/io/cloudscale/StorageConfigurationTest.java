package io.cloudscale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class StorageConfigurationTest {
    @TempDir Path directory;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalFileStore.class, S3StorageConfiguration.class);

    @Test void localRemainsDefaultWithoutAwsCredentials() {
        runner.withPropertyValues("cloudscale.storage-root=" + directory).run(context -> {
            assertThat(context).hasSingleBean(FileStore.class).doesNotHaveBean(S3Client.class);
            assertThat(context.getBean(FileStore.class)).isInstanceOf(LocalFileStore.class);
        });
    }

    @Test void selectsOnlyS3AdapterWhenConfigured() {
        runner.withPropertyValues("cloudscale.storage=s3", "cloudscale.s3.bucket=test-bucket", "cloudscale.s3.region=us-east-1")
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStore.class).hasSingleBean(S3Client.class);
                    assertThat(context.getBean(FileStore.class)).isInstanceOf(S3FileStore.class);
                });
    }

    @Test void missingS3BucketFailsAtStartup() {
        runner.withPropertyValues("cloudscale.storage=s3", "cloudscale.s3.region=us-east-1").run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("cloudscale.s3.bucket is required"));
    }

    @Test void missingS3RegionFailsAtStartup() {
        runner.withPropertyValues("cloudscale.storage=s3", "cloudscale.s3.bucket=test-bucket").run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("cloudscale.s3.region is required"));
    }
}
