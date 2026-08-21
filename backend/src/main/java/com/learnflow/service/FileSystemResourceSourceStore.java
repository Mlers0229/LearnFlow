package com.learnflow.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

public class FileSystemResourceSourceStore implements ResourceSourceStore {
    private final Path root;

    public FileSystemResourceSourceStore(Path configuredRoot) {
        this.root = configuredRoot.toAbsolutePath().normalize();
    }

    @Override
    public String put(String key, InputStream input, long contentLength, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(temporary) != contentLength) {
                throw new IOException("Resource source length changed during storage");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return key;
    }

    @Override public InputStream open(String key) throws IOException { return Files.newInputStream(resolve(key)); }
    @Override public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }

    private Path resolve(String key) throws IOException {
        if (key == null || key.isBlank() || key.contains("\\")) throw new IOException("Invalid object key");
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw new IOException("Object key escapes configured root");
        return resolved;
    }
}
