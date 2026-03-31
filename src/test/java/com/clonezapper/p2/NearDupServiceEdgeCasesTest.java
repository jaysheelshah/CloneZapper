package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.service.NearDupService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("P2")
class NearDupServiceEdgeCasesTest extends BaseTest {

    @Autowired
    NearDupService nearDup;

    @Test
    void shingleOrderDoesNotAffectSignature() {
        List<String> ordered  = List.of("apple", "banana", "cherry", "date");
        List<String> shuffled = new ArrayList<>(ordered);
        Collections.shuffle(shuffled);

        byte[] sigOrdered  = nearDup.computeSignature(ordered);
        byte[] sigShuffled = nearDup.computeSignature(shuffled);
        // MinHash is set-based — order must not matter
        assertThat(nearDup.estimateSimilarity(sigOrdered, sigShuffled)).isEqualTo(1.0);
    }

    @Test
    void partialOverlapProducesIntermediateSimilarity() {
        // 10 shingles each, 5 shared → Jaccard = 5/15 ≈ 0.33
        List<String> a = List.of("s1","s2","s3","s4","s5","s6","s7","s8","s9","s10");
        List<String> b = List.of("s6","s7","s8","s9","s10","s11","s12","s13","s14","s15");

        double sim = nearDup.estimateSimilarity(
            nearDup.computeSignature(a),
            nearDup.computeSignature(b));

        // Expected Jaccard ≈ 0.33 — allow generous tolerance for 128 hashes
        assertThat(sim).isBetween(0.15, 0.55);
    }

    @Test
    void highOverlapProducesHighSimilarity() {
        // 10 shingles, 9 shared → Jaccard = 9/11 ≈ 0.82
        List<String> a = List.of("s1","s2","s3","s4","s5","s6","s7","s8","s9","s10");
        List<String> b = List.of("s1","s2","s3","s4","s5","s6","s7","s8","s9","s99");

        double sim = nearDup.estimateSimilarity(
            nearDup.computeSignature(a),
            nearDup.computeSignature(b));

        assertThat(sim).isGreaterThan(0.6);
    }

    @Test
    void singleShingleIdenticalSets() {
        List<String> shingles = List.of("only");
        byte[] sig = nearDup.computeSignature(shingles);
        assertThat(nearDup.estimateSimilarity(sig, sig)).isEqualTo(1.0);
    }

    @Test
    void signatureLengthMismatchThrows() {
        byte[] short_ = new byte[8];
        byte[] long_  = new byte[NearDupService.NUM_HASHES * Long.BYTES];
        assertThatThrownBy(() -> nearDup.estimateSimilarity(short_, long_))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mismatch");
    }

    @Test
    void deterministicAcrossCallsWithSameSeed() {
        List<String> shingles = List.of("determinism", "is", "important");
        byte[] first  = nearDup.computeSignature(shingles);
        byte[] second = nearDup.computeSignature(shingles);
        assertThat(first).isEqualTo(second);
    }
}
