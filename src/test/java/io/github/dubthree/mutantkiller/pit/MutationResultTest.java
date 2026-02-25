package io.github.dubthree.mutantkiller.pit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MutationResultTest {

    private MutationResult make(String status, String mutator, String description) {
        return new MutationResult(
            "com.example.Foo", "doStuff", "(I)V", 42,
            mutator, description, status, "Foo.java", null
        );
    }

    // --- survived() ---

    @Test
    void survived_returnsTrueForSURVIVED() {
        assertTrue(make("SURVIVED", "x", null).survived());
    }

    @Test
    void survived_returnsTrueForNO_COVERAGE() {
        assertTrue(make("NO_COVERAGE", "x", null).survived());
    }

    @Test
    void survived_returnsFalseForKILLED() {
        assertFalse(make("KILLED", "x", null).survived());
    }

    @Test
    void survived_returnsFalseForTIMED_OUT() {
        assertFalse(make("TIMED_OUT", "x", null).survived());
    }

    @Test
    void survived_returnsFalseForNull() {
        assertFalse(make(null, "x", null).survived());
    }

    // --- killed() ---

    @Test
    void killed_returnsTrueForKILLED() {
        assertTrue(make("KILLED", "x", null).killed());
    }

    @Test
    void killed_returnsFalseForSURVIVED() {
        assertFalse(make("SURVIVED", "x", null).killed());
    }

    @Test
    void killed_returnsFalseForNull() {
        assertFalse(make(null, "x", null).killed());
    }

    // --- humanReadable() ---

    @Test
    void humanReadable_formatsCorrectly() {
        var r = make("SURVIVED",
            "org.pitest.mutationtest.engine.gregor.mutators.MathMutator", null);
        assertEquals("com.example.Foo.doStuff (line 42): Changed math operator (e.g., + to -)",
            r.humanReadable());
    }

    // --- getMutatorDescription() ---

    @ParameterizedTest
    @ValueSource(strings = {
        "ConditionalsBoundaryMutator",
        "IncrementsMutator",
        "MathMutator",
        "NegateConditionalsMutator",
        "ReturnValsMutator",
        "VoidMethodCallMutator",
        "EmptyObjectReturnValsMutator",
        "FalseReturnValsMutator",
        "TrueReturnValsMutator",
        "NullReturnValsMutator",
        "PrimitiveReturnsMutator"
    })
    void getMutatorDescription_knownMutators(String shortName) {
        String full = "org.pitest.mutationtest.engine.gregor.mutators." + shortName;
        var r = make("SURVIVED", full, null);
        String desc = r.getMutatorDescription();
        assertNotNull(desc);
        assertFalse(desc.contains("org.pitest"), "Known mutator should not return raw class name");
    }

    @Test
    void getMutatorDescription_unknownMutator_returnsDescriptionIfPresent() {
        var r = make("SURVIVED", "com.custom.Mutator", "custom description");
        assertEquals("custom description", r.getMutatorDescription());
    }

    @Test
    void getMutatorDescription_unknownMutator_nullDescription_returnsMutator() {
        var r = make("SURVIVED", "com.custom.Mutator", null);
        assertEquals("com.custom.Mutator", r.getMutatorDescription());
    }

    // --- record accessors ---

    @Test
    void recordAccessors() {
        var r = new MutationResult("A", "b", "(V)I", 7, "m", "d", "KILLED", "A.java", "testX");
        assertEquals("A", r.mutatedClass());
        assertEquals("b", r.mutatedMethod());
        assertEquals("(V)I", r.methodDescription());
        assertEquals(7, r.lineNumber());
        assertEquals("m", r.mutator());
        assertEquals("d", r.description());
        assertEquals("KILLED", r.status());
        assertEquals("A.java", r.sourceFile());
        assertEquals("testX", r.killingTest());
    }
}
