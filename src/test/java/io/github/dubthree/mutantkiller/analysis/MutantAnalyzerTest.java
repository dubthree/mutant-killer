package io.github.dubthree.mutantkiller.analysis;

import io.github.dubthree.mutantkiller.build.TestFramework;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MutantAnalyzerTest {

    @TempDir
    Path project;

    Path srcRoot;
    Path testRoot;

    private static final String SOURCE = """
        package com.example;

        public class Calculator {
            public int add(int a, int b) {
                return a + b;
            }

            public int add(int a) {
                return a + 1;
            }

            public static class Inner {
                public int size() {
                    return 3;
                }
            }
        }
        """;

    @BeforeEach
    void setUp() throws Exception {
        srcRoot = project.resolve("src/main/java");
        testRoot = project.resolve("src/test/java");
        Files.createDirectories(srcRoot.resolve("com/example"));
        Files.createDirectories(testRoot.resolve("com/example/junit"));
        Files.writeString(srcRoot.resolve("com/example/Calculator.java"), SOURCE);
    }

    private static MutationResult mutant(String cls, String method, int line) {
        return new MutationResult(cls, method, "(I)I", line, "org.x.MathMutator", "Replaced addition", "SURVIVED", "Calculator.java", null, 1, 0);
    }

    @Test
    void findsTestInAnotherPackageAndDetectsFrameworkFromIt() throws Exception {
        Path test = testRoot.resolve("com/example/junit/CalculatorTest.java");
        Files.writeString(test, "package com.example.junit;\n\nimport org.junit.Test;\n\npublic class CalculatorTest {\n}\n");

        MutantAnalyzer analyzer = new MutantAnalyzer(List.of(srcRoot), List.of(testRoot), TestFramework.JUNIT5);
        MutantAnalysis a = analyzer.analyze(mutant("com.example.Calculator", "add", 9));

        assertEquals(test, a.testFile());
        assertEquals("com.example.junit.CalculatorTest", a.testClassFqn());
        assertTrue(a.hasExistingTest());
        assertEquals(TestFramework.JUNIT4, a.testFramework());
        // the overload containing line 9 is chosen, not the first one
        assertTrue(a.methodSource().contains("return a + 1;"), a.methodSource());
        assertFalse(a.methodSource().contains("int b"));
        assertTrue(a.contextAroundMutation().contains(">>>    9:"), a.contextAroundMutation());
    }

    @Test
    void innerClassMapsToOuterSourceAndNewTestPath() throws Exception {
        MutantAnalyzer analyzer = new MutantAnalyzer(List.of(srcRoot), List.of(testRoot), TestFramework.JUNIT5);
        MutantAnalysis a = analyzer.analyze(mutant("com.example.Calculator$Inner", "size", 14));

        assertEquals(srcRoot.resolve("com/example/Calculator.java"), a.sourceFile());
        assertFalse(a.hasExistingTest());
        assertEquals(testRoot.resolve("com/example/CalculatorTest.java"), a.testFile());
        assertEquals("com.example.CalculatorTest", a.testClassFqn());
        assertEquals("com.example", a.testPackage());
        assertEquals("CalculatorTest", a.testSimpleName());
        assertEquals(TestFramework.JUNIT5, a.testFramework());
        assertTrue(a.methodSource().contains("return 3;"));
    }

    @Test
    void missingSourceIsAnError() {
        MutantAnalyzer analyzer = new MutantAnalyzer(List.of(srcRoot), List.of(testRoot), TestFramework.JUNIT5);
        assertThrows(java.io.IOException.class, () -> analyzer.analyze(mutant("com.example.Missing", "x", 1)));
    }

    @Test
    void defaultPackageClassWorks() throws Exception {
        Files.writeString(srcRoot.resolve("Top.java"), "public class Top { int f() { return 1; } }\n");
        MutantAnalyzer analyzer = new MutantAnalyzer(List.of(srcRoot), List.of(testRoot), TestFramework.JUNIT5);
        MutantAnalysis a = analyzer.analyze(mutant("Top", "f", 1));
        assertEquals(testRoot.resolve("TopTest.java"), a.testFile());
        assertEquals("TopTest", a.testClassFqn());
        assertEquals("", a.testPackage());
    }

    @Test
    void multiModuleTestRootFollowsSourceModule() throws Exception {
        Path modSrc = project.resolve("core/src/main/java");
        Path modTest = project.resolve("core/src/test/java");
        Files.createDirectories(modSrc.resolve("org/core"));
        Files.createDirectories(modTest);
        Files.writeString(modSrc.resolve("org/core/Thing.java"), "package org.core;\npublic class Thing { int f() { return 1; } }\n");
        MutantAnalyzer analyzer = new MutantAnalyzer(List.of(srcRoot, modSrc), List.of(testRoot, modTest), TestFramework.JUNIT5);
        MutantAnalysis a = analyzer.analyze(mutant("org.core.Thing", "f", 2));
        assertEquals(modTest.resolve("org/core/ThingTest.java"), a.testFile());
    }
}
