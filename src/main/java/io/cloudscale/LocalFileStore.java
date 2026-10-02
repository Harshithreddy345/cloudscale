package io.cloudscale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Component
public class LocalFileStore implements FileStore {
    private final Path root;
    public LocalFileStore(@Value("${cloudscale.storage-root:data}") String root) throws IOException {
        this.root = Path.of(root).toAbsolutePath();
        Files.createDirectories(this.root.resolve("uploads"));
        Files.createDirectories(this.root.resolve("results"));
    }
    // Keys are generated UUIDs, never user-supplied paths or filenames.
    public void saveInput(UUID id, InputStream input) throws IOException { Files.copy(input, root.resolve("uploads/" + id + ".csv")); }
    public InputStream openInput(UUID id) throws IOException { return Files.newInputStream(root.resolve("uploads/" + id + ".csv")); }
    public void saveResult(UUID id, byte[] result) throws IOException { Files.write(root.resolve("results/" + id + ".csv"), result); }
    public byte[] readResult(UUID id) throws IOException { return Files.readAllBytes(root.resolve("results/" + id + ".csv")); }
}
