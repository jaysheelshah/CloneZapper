package com.clonezapper.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

@Component
public class LocalFilesystemProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemProvider.class);

    @Override
    public String getName() {
        return "local";
    }

    /**
     * Walks the filesystem from rootPath, yielding regular files only.
     * Skips symlinks and files that cannot be read (logs a warning and continues).
     */
    @Override
    public Stream<Path> enumerate(String rootPath) throws IOException {
        Path root = Path.of(rootPath);
        if (!Files.exists(root)) {
            throw new IOException("Path does not exist: " + rootPath);
        }
        if (!Files.isDirectory(root)) {
            return Stream.of(root);
        }
        return Files.walk(root, FileVisitOption.FOLLOW_LINKS)
            .filter(path -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    return attrs.isRegularFile();
                } catch (IOException e) {
                    log.warn("Skipping unreadable path {}: {}", path, e.getMessage());
                    return false;
                }
            });
    }
}
