package io.cloudscale;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cloudscale.dispatch", havingValue = "sqs")
public class SqsConfiguration {
    @Bean String sqsQueueUrl(@Value("${cloudscale.sqs.queue-url:}") String url,
                           @Value("${cloudscale.storage:local}") String storage,
                           @Value("${cloudscale.metadata:memory}") String metadata) {
        if (url.isBlank()) throw new IllegalArgumentException("cloudscale.sqs.queue-url is required");
        if (!storage.equals("s3") || !metadata.equals("dynamodb")) throw new IllegalArgumentException("SQS requires S3 and DynamoDB profiles");
        return url.trim();
    }
    @Bean(destroyMethod = "close") SqsClient sqsClient(@Value("${cloudscale.sqs.region:}") String region) {
        if (region.isBlank()) throw new IllegalArgumentException("cloudscale.sqs.region is required");
        return SqsClient.builder().region(Region.of(region.trim()))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(20)).apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
    }
    @Bean @ConditionalOnProperty(name = "cloudscale.api.enabled", havingValue = "true", matchIfMissing = true)
    JobDispatcher sqsDispatcher(SqsClient client, String sqsQueueUrl) { return new SqsJobDispatcher(client, sqsQueueUrl); }
    @Bean @ConditionalOnProperty(name = "cloudscale.worker.enabled", havingValue = "true")
    SqsJobConsumer sqsConsumer(SqsClient client, String sqsQueueUrl, JobWorker worker) { return new SqsJobConsumer(client, sqsQueueUrl, worker); }
}
