package io.cloudscale;
import java.io.*;
import java.util.UUID;
public interface FileStore {
    void saveInput(UUID id, InputStream input) throws IOException;
    InputStream openInput(UUID id) throws IOException;
    void saveResult(UUID id, byte[] result) throws IOException;
    byte[] readResult(UUID id) throws IOException;
}
