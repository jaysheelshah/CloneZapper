package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.service.NearDupService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("P1")
class NearDupServiceTest extends BaseTest {

    @Autowired
    NearDupService nearDup;

    @Test
    void identicalShingleSetsHaveSimilarityOne() {
        List<String> shingles = List.of("the", "quick", "brown", "fox", "jumps");
        byte[] sigA = nearDup.computeSignature(shingles);
        byte[] sigB = nearDup.computeSignature(shingles);
        assertThat(nearDup.estimateSimilarity(sigA, sigB)).isEqualTo(1.0);
    }

    @Test
    void disjointShingleSetsHaveSimilarityNearZero() {
        List<String> a = List.of("alpha", "bravo", "charlie", "delta", "echo",
                                  "foxtrot", "golf", "hotel", "india", "juliet");
        List<String> b = List.of("kilo", "lima", "mike", "november", "oscar",
                                  "papa", "quebec", "romeo", "sierra", "tango");
        byte[] sigA = nearDup.computeSignature(a);
        byte[] sigB = nearDup.computeSignature(b);
        assertThat(nearDup.estimateSimilarity(sigA, sigB)).isLessThan(0.1);
    }

    @Test
    void signatureHasExpectedLength() {
        List<String> shingles = List.of("hello", "world");
        byte[] sig = nearDup.computeSignature(shingles);
        assertThat(sig).hasSize(NearDupService.NUM_HASHES * Long.BYTES);
    }

    @Test
    void emptyShingleListDoesNotThrow() {
        byte[] sig = nearDup.computeSignature(List.of());
        assertThat(sig).hasSize(NearDupService.NUM_HASHES * Long.BYTES);
    }
}
