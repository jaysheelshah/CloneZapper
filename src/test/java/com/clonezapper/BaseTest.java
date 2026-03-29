package com.clonezapper;

import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base class for all CloneZapper tests.
 *
 * Provides:
 *  - {@code @TempDir} for isolated filesystem fixtures
 *  - Helper methods to create test files with known content
 *  - Spring Boot test context with the "test" profile (in-memory SQLite, no web)
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseTest {

    @TempDir
    protected Path tempDir;

    /**
     * Create a file with UTF-8 text content under {@code tempDir}.
     * Parent directories are created automatically.
     */
    protected Path createFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    /**
     * Create a file with raw byte content under {@code tempDir}.
     */
    protected Path createFile(String relativePath, byte[] content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return file;
    }

    /**
     * Create an empty directory under {@code tempDir}.
     */
    protected Path createDir(String relativePath) throws IOException {
        Path dir = tempDir.resolve(relativePath);
        Files.createDirectories(dir);
        return dir;
    }
}
