package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.engine.UnifiedScanner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — UnifiedScanner emits phase transitions via the progress callback.
 */
@Tag("P1")
class ScanProgressCallbackTest extends BaseTest {

    @Autowired UnifiedScanner scanner;

    @Test
    void callbackReceivesAllPhases() throws IOException {
        createFile("a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("b.dat", TestFixtures.IDENTICAL_CONTENT);

        List<String> phases = new ArrayList<>();
        scanner.startScan(List.of(tempDir.toString()), phases::add);

        assertThat(phases).contains("SCANNING", "CANDIDATES", "COMPARING", "CLUSTERING", "COMPLETE");
    }

    @Test
    void phasesAreEmittedInCorrectOrder() throws IOException {
        createFile("a.dat", TestFixtures.CONTENT_A);

        List<String> phases = new ArrayList<>();
        scanner.startScan(List.of(tempDir.toString()), phases::add);

        List<String> expected = List.of("SCANNING", "CANDIDATES", "COMPARING", "CLUSTERING", "COMPLETE");
        assertThat(phases).containsExactlyElementsOf(expected);
    }

    @Test
    void noCallbackOverloadStillCompletes() throws IOException {
        createFile("a.dat", TestFixtures.CONTENT_A);
        // Should not throw — default no-op callback
        var run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
    }

    @Test
    void completePhaseMeansRunIsMarkedComplete() throws IOException {
        createFile("a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("b.dat", TestFixtures.IDENTICAL_CONTENT);

        String[] lastPhase = {null};
        var run = scanner.startScan(List.of(tempDir.toString()), p -> lastPhase[0] = p);

        assertThat(lastPhase[0]).isEqualTo("COMPLETE");
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
    }
}
