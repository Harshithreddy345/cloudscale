package io.cloudscale;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cloudscale.metadata", havingValue = "dynamodb")
public class DynamoMetadataConfiguration {
    @Bean(destroyMethod = "close")
    DynamoDbClient dynamoDbClient(@Value("${cloudscale.dynamodb.region:}") String region) {
        if (region.isBlank()) throw new IllegalArgumentException("cloudscale.dynamodb.region is required");
        return DynamoDbClient.builder().region(Region.of(region.trim()))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(15)))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
    }
    @Bean JobRepository dynamoJobRepository(DynamoDbClient client, @Value("${cloudscale.dynamodb.table:}") String table) {
        return new DynamoJobRepository(client, table);
    }
}
