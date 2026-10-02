package io.cloudscale;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.assertj.core.api.Assertions.*;

/** Real AWS SDK HTTP requests against an in-process S3 protocol stub, never a real AWS account. */
class S3HttpIntegrationTest {
    private HttpServer server;
    private S3Client client;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final List<String> signatures = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                signatures.add(Objects.toString(exchange.getRequestHeaders().getFirst("Authorization"), ""));
                String key = exchange.getRequestURI().getPath();
                if (exchange.getRequestMethod().equals("PUT")) {
                    byte[] body = exchange.getRequestBody().readAllBytes();
                    if (Objects.toString(exchange.getRequestHeaders().getFirst("Content-Encoding"), "").contains("aws-chunked"))
                        body = decodeAwsChunks(body);
                    objects.put(key, body);
                    exchange.getResponseHeaders().add("ETag", "\"test-etag\"");
                    exchange.sendResponseHeaders(200, -1);
                } else if (objects.containsKey(key)) {
                    byte[] body = objects.get(key);
                    exchange.getResponseHeaders().add("Content-Type", "text/csv");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } else {
                    byte[] body = "<Error><Code>NoSuchKey</Code><Message>Missing object</Message></Error>".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/xml");
                    exchange.sendResponseHeaders(404, body.length);
                    exchange.getResponseBody().write(body);
                }
            }
        });
        server.start();
        client = S3Client.builder().region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access", "test-secret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(10)))
                .build();
    }

    @AfterEach void stop() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    @Test void realSdkRoundTripAndWorkerReport() throws Exception {
        var files = new S3FileStore(client, "test-bucket");
        var id = UUID.randomUUID();
        byte[] fixture = Files.readAllBytes(Path.of("sample-data/sales.csv"));
        try (var input = new ByteArrayInputStream(fixture)) { files.saveInput(id, input); }
        var jobs = new InMemoryJobRepository();
        jobs.create(new Job(id, "sales.csv", Job.Status.QUEUED, Instant.now(), null, null, null));
        new JobWorker(jobs, files, new SalesProcessor()).process(id);
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(Job.Status.COMPLETED);
        assertThat(objects.get("/test-bucket/uploads/" + id + ".csv")).isEqualTo(fixture);
        assertThat(new String(files.readResult(id), StandardCharsets.UTF_8))
                .contains("revenue,all,1179.97", "invalid_rows,all,1");
        assertThat(objects).containsKey("/test-bucket/results/" + id + ".csv");
        assertThat(signatures).hasSize(4).allMatch(value -> value.startsWith("AWS4-HMAC-SHA256"));
    }

    @Test void realSdkMissingObjectBecomesIoFailure() {
        assertThatThrownBy(() -> new S3FileStore(client, "test-bucket").openInput(UUID.randomUUID()))
                .isInstanceOf(IOException.class).hasCauseInstanceOf(software.amazon.awssdk.services.s3.model.NoSuchKeyException.class);
    }

    private static byte[] decodeAwsChunks(byte[] body) throws IOException {
        var input = new ByteArrayInputStream(body);
        var result = new ByteArrayOutputStream();
        while (true) {
            var line = new StringBuilder();
            int value;
            while ((value = input.read()) != -1 && value != '\r') line.append((char) value);
            if (value == -1 || input.read() != '\n') throw new IOException("Invalid chunk header");
            int size = Integer.parseInt(line.toString().split(";", 2)[0], 16);
            if (size == 0) return result.toByteArray();
            byte[] chunk = input.readNBytes(size);
            if (chunk.length != size || input.read() != '\r' || input.read() != '\n') throw new IOException("Invalid chunk body");
            result.write(chunk);
        }
    }
}
