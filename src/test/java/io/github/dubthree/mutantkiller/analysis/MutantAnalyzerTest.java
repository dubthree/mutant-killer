package io.github.dubthree.mutantkiller.analysis;

import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MutantAnalyzerTest {

    @TempDir
    Path tempDir;
    Path sourceDir;
    Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        sourceDir = tempDir.resolve("src/main/java");
        testDir = tempDir.resolve("src/test/java");

        // Create source file
        Path srcPkg = sourceDir.resolve("com/example");
        Files.createDirectories(srcPkg);
        Files.writeString(srcPkg.resolve("Calculator.java"), """
            package com.example;
            
            public class Calculator {
                public int add(int a, int b) {
                    return a + b;
                }
                
                public int subtract(int a, int b) {
                    return a - b;
                }
            }
            """);

        // Create test file
        Path testPkg = testDir.resolve("com/example");
        Files.createDirectories(testPkg);
        Files.writeString(testPkg.resolve("CalculatorTest.java"), """
            package com.example;
            
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;
            
            class CalculatorTest {
                @Test
                void testAdd() {
                    assertEquals(3, new Calculator().add(1, 2));
                }
            }
            """);
    }

    private MutantAnalyzer makeAnalyzer() {
        var config = new MutantKillerConfig(
            "test-model", "fake-key", sourceDir, testDir, null, true, false);
        return new MutantAnalyzer(config);
    }

    @Test
    void analyze_findsSourceAndTest() throws IOException {
        var mutation = new MutationResult(
            "com.example.Calculator", "add", "(II)I", 5,
            "org.pitest.mutationtest.engine.gregor.mutators.MathMutator",
            "replaced + with -", "SURVIVED", "Calculator.java", null);

        MutantAnalysis analysis = makeAnalyzer().analyze(mutation);

        assertNotNull(analysis.sourceFile());
        assertNotNull(analysis.sourceCode());
        assertTrue(analysis.sourceCode().contains("return a + b"));
        assertTrue(analysis.hasExistingTest());
        assertTrue(analysis.existingTestCode().contains("testAdd"));
    }

    @Test
    void analyze_extractsContext() throws IOException {
        var mutation = new MutationResult(
            "com.example.Calculator", "add", "(II)I", 5,
            "org.pitest.mutationtest.engine.gregor.mutators.MathMutator",
            "replaced + with -", "SURVIVED", "Calculator.java", null);

        MutantAnalysis analysis = makeAnalyzer().analyze(mutation);

        assertNotNull(analysis.contextAroundMutation());
        assertTrue(analysis.contextAroundMutation().contains(">>>"));
    }

    @Test
    void analyze_findsMutatedMethod() throws IOException {
        var mutation = new MutationResult(
            "com.example.Calculator", "add", "(II)I", 5,
            "org.pitest.mutationtest.engine.gregor.mutators.MathMutator",
            "replaced + with -", "SURVIVED", "Calculator.java", null);

        MutantAnalysis analysis = makeAnalyzer().analyze(mutation);

        assertNotNull(analysis.mutatedMethod());
        assertTrue(analysis.mutatedMethod().contains("add"));
    }

    @Test
    void analyze_throwsWhenSourceNotFound() {
        var mutation = new MutationResult(
            "com.example.NonExistent", "foo", "()V", 1,
            "x", "d", "SURVIVED", "NonExistent.java", null);

        assertThrows(IOException.class, () -> makeAnalyzer().analyze(mutation));
    }

    @Test
    void analyze_noTestFile() throws IOException {
        // Create source without matching test
        Path pkg = sourceDir.resolve("com/example");
        Files.writeString(pkg.resolve("Standalone.java"), """
            package com.example;
            public class Standalone {
                public void run() {}
            }
            """);

        var mutation = new MutationResult(
            "com.example.Standalone", "run", "()V", 3,
            "org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator",
            "removed call", "SURVIVED", "Standalone.java", null);

        MutantAnalysis analysis = makeAnalyzer().analyze(mutation);

        assertFalse(analysis.hasExistingTest());
        assertNull(analysis.testFile());
        assertNull(analysis.existingTestCode());
    }
}
