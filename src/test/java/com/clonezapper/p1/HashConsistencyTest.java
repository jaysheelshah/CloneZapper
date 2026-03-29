package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.service.HashService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — Hash consistency guarantees.
 * The hash function is the foundation of all dedup decisions.
 * Any inconsistency here causes false negatives (missed duplicates).
 */
@Tag("P1")
class HashConsistencyTest extends BaseTest {

    @Autowired
    private HashService hashService;

    @Test
    void sameFileAlwaysProducesSameFullHash() throws IOException {
        Path file = createFile("test.txt", "hello world");
        String hash1 = hashService.computeFullHash(file);
        String hash2 = hashService.computeFullHash(file);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void identicalContentProducesSameFullHash() throws IOException {
        Path file1 = createFile("a/original.txt", TestFixtures.IDENTICAL_CONTENT);
        Path file2 = createFile("b/duplicate.txt", TestFixtures.IDENTICAL_CONTENT);
        assertThat(hashService.computeFullHash(file1))
            .isEqualTo(hashService.computeFullHash(file2));
    }

    @Test
    void differentContentProducesDifferentFullHash() throws IOException {
        Path file1 = createFile("a.txt", TestFixtures.CONTENT_A);
        Path file2 = createFile("b.txt", TestFixtures.CONTENT_B);
        assertThat(hashService.computeFullHash(file1))
            .isNotEqualTo(hashService.computeFullHash(file2));
    }

    @Test
    void sameFileAlwaysProducesSamePartialHash() throws IOException {
        Path file = createFile("large.bin", TestFixtures.randomContent(64 * 1024));
        String hash1 = hashService.computePartialHash(file);
        String hash2 = hashService.computePartialHash(file);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void identicalContentProducesSamePartialHash() throws IOException {
        byte[] content = TestFixtures.randomContent(1024);
        Path file1 = createFile("x/file.bin", content);
        Path file2 = createFile("y/file.bin", content);
        assertThat(hashService.computePartialHash(file1))
            .isEqualTo(hashService.computePartialHash(file2));
    }

    @Test
    void emptyFileHashIsConsistent() throws IOException {
        Path file1 = createFile("empty1.txt", new byte[0]);
        Path file2 = createFile("empty2.txt", new byte[0]);
        assertThat(hashService.computeFullHash(file1))
            .isEqualTo(hashService.computeFullHash(file2));
    }
}
