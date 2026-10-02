package io.cloudscale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cloudscale.storage", havingValue = "s3")
public class S3StorageConfiguration {
    @Bean(destroyMethod = "close")
    DefaultCredentialsProvider s3CredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${cloudscale.s3.region:}") String region,
                      DefaultCredentialsProvider credentials) {
        if (region.isBlank()) throw new IllegalArgumentException("cloudscale.s3.region is required for S3 storage");
        return S3Client.builder().region(Region.of(region.trim())).credentialsProvider(credentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofMinutes(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(45)))
                .build();
    }

    @Bean
    FileStore s3FileStore(S3Client client, @Value("${cloudscale.s3.bucket:}") String bucket) {
        return new S3FileStore(client, bucket);
    }
}
