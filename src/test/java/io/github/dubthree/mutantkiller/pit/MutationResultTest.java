package io.github.dubthree.mutantkiller.pit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MutationResultTest {

    private static MutationResult m(String cls, String status, int index) {
        return new MutationResult(cls, "size", "()I", 30, "org.x.PrimitiveReturnsMutator", "replaced int return with 0", status, "Calculator.java", null, index, 0);
    }

    @Test
    void outerClassStripsInnerClassSuffix() {
        MutationResult inner = m("com.example.Calculator$Inner", "SURVIVED", 1);
        assertEquals("com.example.Calculator", inner.outerClass());
        assertEquals("com.example", inner.packageName());
        assertEquals("Calculator", inner.simpleOuterClassName());
    }

    @Test
    void defaultPackageHasEmptyPackageName() {
        MutationResult top = m("Calculator", "SURVIVED", 1);
        assertEquals("", top.packageName());
        assertEquals("Calculator", top.simpleOuterClassName());
    }

    @Test
    void sameMutantIgnoresStatus() {
        assertTrue(m("a.B", "SURVIVED", 1).sameMutant(m("a.B", "KILLED", 1)));
        assertFalse(m("a.B", "SURVIVED", 1).sameMutant(m("a.B", "SURVIVED", 2)));
        assertFalse(m("a.B", "SURVIVED", 1).sameMutant(null));
    }

    @Test
    void mutatorDescriptionFallsBackToKnownMutators() {
        MutationResult r = new MutationResult("a.B", "f", "()V", 1,
            "org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator", null, "SURVIVED", null, null, 0, 0);
        assertEquals("Removed void method call", r.getMutatorDescription());
        MutationResult unknown = new MutationResult("a.B", "f", "()V", 1, "org.x.WeirdMutator", "", "SURVIVED", null, null, 0, 0);
        assertEquals("org.x.WeirdMutator", unknown.getMutatorDescription());
    }

    @Test
    void nullStatusBecomesUnknown() {
        MutationResult r = new MutationResult(null, null, null, 1, null, null, null, null, null, 0, 0);
        assertEquals("UNKNOWN", r.status());
        assertFalse(r.survived());
        assertFalse(r.isDetected());
    }
}
