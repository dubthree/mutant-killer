package io.github.dubthree.mutantkiller.analysis;

import io.github.dubthree.mutantkiller.pit.MutationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MutantAnalysisTest {

    private MutationResult mutation() {
        return new MutationResult(
            "com.example.Foo", "doStuff", "(I)V", 10,
            "org.pitest.mutationtest.engine.gregor.mutators.MathMutator",
            "replaced + with -", "SURVIVED", "Foo.java", null
        );
    }

    @Test
    void hasExistingTest_trueWhenBothPresent() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            "method", "context", Path.of("FooTest.java"), "test code");
        assertTrue(a.hasExistingTest());
    }

    @Test
    void hasExistingTest_falseWhenTestFileNull() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            "method", "context", null, "test code");
        assertFalse(a.hasExistingTest());
    }

    @Test
    void hasExistingTest_falseWhenTestCodeNull() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            "method", "context", Path.of("FooTest.java"), null);
        assertFalse(a.hasExistingTest());
    }

    @Test
    void hasExistingTest_falseWhenBothNull() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            "method", "context", null, null);
        assertFalse(a.hasExistingTest());
    }

    @Test
    void buildAnalysisPrompt_containsMutationDetails() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            "void doStuff(int x) { }", ">>> 10: x + y", null, null);
        String prompt = a.buildAnalysisPrompt();

        assertTrue(prompt.contains("com.example.Foo"));
        assertTrue(prompt.contains("doStuff"));
        assertTrue(prompt.contains("10"));
        assertTrue(prompt.contains(">>> 10: x + y"));
        assertTrue(prompt.contains("Changed math operator"));
    }

    @Test
    void buildAnalysisPrompt_includesExistingTest() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            "method body", "context", Path.of("FooTest.java"), "existing test code here");
        String prompt = a.buildAnalysisPrompt();

        assertTrue(prompt.contains("Existing Test Class"));
        assertTrue(prompt.contains("existing test code here"));
    }

    @Test
    void buildAnalysisPrompt_omitsTestSectionWhenNoTest() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            "method body", "context", null, null);
        String prompt = a.buildAnalysisPrompt();

        assertFalse(prompt.contains("Existing Test Class"));
    }

    @Test
    void buildAnalysisPrompt_includesFullMethod() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            "public int doStuff(int x) { return x + 1; }", "context", null, null);
        String prompt = a.buildAnalysisPrompt();

        assertTrue(prompt.contains("Full Method"));
        assertTrue(prompt.contains("return x + 1"));
    }

    @Test
    void buildAnalysisPrompt_omitsMethodSectionWhenNull() {
        var a = new MutantAnalysis(mutation(), Path.of("Foo.java"), "code",
            null, "context", null, null);
        String prompt = a.buildAnalysisPrompt();

        assertFalse(prompt.contains("Full Method"));
    }
}
