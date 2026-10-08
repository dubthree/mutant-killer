package io.github.dubthree.mutantkiller.pit;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PitReportParserTest {

    private static File sample() {
        return new File(PitReportParserTest.class.getResource("/sample-mutations.xml").getFile());
    }

    @Test
    void parsesAllMutationsIncludingUnknownElements() throws Exception {
        List<MutationResult> results = new PitReportParser().parse(sample());
        assertEquals(4, results.size());

        MutationResult killed = results.get(0);
        assertEquals("com.example.Calculator", killed.mutatedClass());
        assertEquals("add", killed.mutatedMethod());
        assertEquals("(II)I", killed.methodDescription());
        assertEquals(10, killed.lineNumber());
        assertEquals("KILLED", killed.status());
        assertEquals(5, killed.index());
        assertEquals(0, killed.block());
        assertTrue(killed.killed());
        assertTrue(killed.isDetected());
        assertEquals("Replaced integer addition with subtraction", killed.getMutatorDescription());
    }

    @Test
    void readsLegacyScalarIndexElements() throws Exception {
        List<MutationResult> results = new PitReportParser().parse(sample());
        MutationResult legacy = results.get(2);
        assertEquals(2, legacy.index());
        assertTrue(legacy.isNoCoverage());
        assertTrue(legacy.survived());
        assertFalse(legacy.isSurvived());
    }

    @Test
    void timedOutCountsAsDetected() throws Exception {
        List<MutationResult> results = new PitReportParser().parse(sample());
        assertTrue(results.get(3).isDetected());
        assertFalse(results.get(3).killed());
    }

    @Test
    void parseAllConcatenates() throws Exception {
        assertEquals(8, new PitReportParser().parseAll(List.of(sample(), sample())).size());
    }
}
