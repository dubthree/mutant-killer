package io.github.dubthree.mutantkiller.pit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PitReportParserTest {

    private final PitReportParser parser = new PitReportParser();

    @Test
    void parse_standardReport() throws IOException {
        File report = new File(getClass().getClassLoader().getResource("mutations.xml").getFile());
        List<MutationResult> results = parser.parse(report);

        assertEquals(5, results.size());
    }

    @Test
    void parse_survivedMutations() throws IOException {
        File report = new File(getClass().getClassLoader().getResource("mutations.xml").getFile());
        List<MutationResult> results = parser.parse(report);

        long survived = results.stream().filter(MutationResult::survived).count();
        assertEquals(3, survived); // 2 SURVIVED + 1 NO_COVERAGE
    }

    @Test
    void parse_killedMutations() throws IOException {
        File report = new File(getClass().getClassLoader().getResource("mutations.xml").getFile());
        List<MutationResult> results = parser.parse(report);

        long killed = results.stream().filter(MutationResult::killed).count();
        assertEquals(2, killed);
    }

    @Test
    void parse_fieldsPopulatedCorrectly() throws IOException {
        File report = new File(getClass().getClassLoader().getResource("mutations.xml").getFile());
        List<MutationResult> results = parser.parse(report);

        MutationResult first = results.get(0);
        assertEquals("com.example.Calculator", first.mutatedClass());
        assertEquals("add", first.mutatedMethod());
        assertEquals("(II)I", first.methodDescription());
        assertEquals(10, first.lineNumber());
        assertTrue(first.mutator().contains("MathMutator"));
        assertEquals("SURVIVED", first.status());
        assertEquals("Calculator.java", first.sourceFile());
    }

    @Test
    void parse_killingTestPopulated() throws IOException {
        File report = new File(getClass().getClassLoader().getResource("mutations.xml").getFile());
        List<MutationResult> results = parser.parse(report);

        MutationResult killed = results.get(1);
        assertEquals("com.example.CalculatorTest.testSubtract", killed.killingTest());
    }

    @Test
    void parse_emptyReport(@TempDir Path tmp) throws IOException {
        File empty = new File(getClass().getClassLoader().getResource("empty-mutations.xml").getFile());
        List<MutationResult> results = parser.parse(empty);
        assertTrue(results.isEmpty());
    }

    @Test
    void parse_nonExistentFile() {
        assertThrows(IOException.class, () -> parser.parse(new File("nonexistent.xml")));
    }

    @Test
    void parse_invalidXml(@TempDir Path tmp) throws IOException {
        Path bad = tmp.resolve("bad.xml");
        Files.writeString(bad, "this is not xml");
        assertThrows(IOException.class, () -> parser.parse(bad.toFile()));
    }

    @Test
    void parse_singleMutation(@TempDir Path tmp) throws IOException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <mutations>
                <mutation detected="true" status="KILLED" numberOfTestsRun="1">
                    <sourceFile>Foo.java</sourceFile>
                    <mutatedClass>com.example.Foo</mutatedClass>
                    <mutatedMethod>bar</mutatedMethod>
                    <methodDescription>()V</methodDescription>
                    <lineNumber>5</lineNumber>
                    <mutator>org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator</mutator>
                    <description>removed call</description>
                    <killingTest>com.example.FooTest.testBar</killingTest>
                </mutation>
            </mutations>
            """;
        Path f = tmp.resolve("single.xml");
        Files.writeString(f, xml);
        List<MutationResult> results = parser.parse(f.toFile());
        assertEquals(1, results.size());
        assertEquals("bar", results.get(0).mutatedMethod());
    }
}
