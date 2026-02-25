package io.github.dubthree.mutantkiller.codegen;

import io.github.dubthree.mutantkiller.analysis.MutantAnalysis;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestImprovementTest {

    private MutationResult mutation() {
        return new MutationResult(
            "com.example.Foo", "bar", "(I)V", 10,
            "org.pitest.mutationtest.engine.gregor.mutators.MathMutator",
            "replaced + with -", "SURVIVED", "Foo.java", null);
    }

    private MutantAnalysis analysisWithTest(Path testFile, String testCode) {
        return new MutantAnalysis(mutation(), Path.of("Foo.java"), "source",
            "method", "context", testFile, testCode);
    }

    private MutantAnalysis analysisWithoutTest() {
        return new MutantAnalysis(mutation(), Path.of("Foo.java"), "source",
            "method", "context", null, null);
    }

    // --- diff() ---

    @Test
    void diff_containsMutationInfo() {
        var imp = new TestImprovement(analysisWithoutTest(), "@Test void t() {}", false);
        String diff = imp.diff();

        assertTrue(diff.contains("Proposed Test Improvement"));
        assertTrue(diff.contains("NEW TEST FILE"));
        assertTrue(diff.contains("com.example.Foo.bar"));
        assertTrue(diff.contains("@Test void t() {}"));
    }

    @Test
    void diff_showsExistingTestFile() {
        var imp = new TestImprovement(
            analysisWithTest(Path.of("FooTest.java"), "existing"),
            "new code", false);
        String diff = imp.diff();

        assertTrue(diff.contains("FooTest.java"));
    }

    // --- generatedCode() ---

    @Test
    void generatedCode_returnsCode() {
        var imp = new TestImprovement(analysisWithoutTest(), "generated code", false);
        assertEquals("generated code", imp.generatedCode());
    }

    // --- apply() dry run ---

    @Test
    void apply_dryRun_doesNothing(@TempDir Path tmp) throws IOException {
        Path testFile = tmp.resolve("FooTest.java");
        Files.writeString(testFile, "class FooTest { }");

        var analysis = analysisWithTest(testFile, "class FooTest { }");
        var imp = new TestImprovement(analysis, "@Test void newTest() {}", true);

        imp.apply(); // Should not throw or modify file

        assertEquals("class FooTest { }", Files.readString(testFile));
    }

    // --- apply() to existing test with parseable code ---

    @Test
    void apply_appendsToExistingTest(@TempDir Path tmp) throws IOException {
        Path testFile = tmp.resolve("FooTest.java");
        String original = """
            package com.example;
            
            import org.junit.jupiter.api.Test;
            
            class FooTest {
                @Test
                void existingTest() {}
            }
            """;
        Files.writeString(testFile, original);

        var analysis = analysisWithTest(testFile, original);
        var imp = new TestImprovement(analysis,
            "@Test\nvoid killMutant() { assertEquals(3, new Foo().bar(1, 2)); }",
            false);

        imp.apply();

        String updated = Files.readString(testFile);
        assertTrue(updated.contains("killMutant"));
        assertTrue(updated.contains("existingTest"));
    }
}
