package io.github.dubthree.mutantkiller.kill;

import io.github.dubthree.mutantkiller.analysis.MutantAnalyzer;
import io.github.dubthree.mutantkiller.build.BuildException;
import io.github.dubthree.mutantkiller.build.BuildExecutor;
import io.github.dubthree.mutantkiller.build.BuildResult;
import io.github.dubthree.mutantkiller.build.TestFramework;
import io.github.dubthree.mutantkiller.codegen.TestImprover;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import io.github.dubthree.mutantkiller.llm.LlmClient;
import io.github.dubthree.mutantkiller.llm.LlmException;
import io.github.dubthree.mutantkiller.llm.LlmResponse;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KillLoopTest {

    @TempDir
    Path project;
    Path srcRoot;
    Path testRoot;
    Path testFile;

    static final MutationResult MUTANT = new MutationResult("com.example.Calc", "add", "(II)I", 4,
        "org.pitest.mutationtest.engine.gregor.mutators.MathMutator", "Replaced integer addition with subtraction",
        "SURVIVED", "Calc.java", null, 1, 0);

    /** Scripted model: returns canned replies in order and records the prompts it saw. */
    static class FakeLlm implements LlmClient {
        final Deque<String> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();

        @Override
        public LlmResponse complete(String systemPrompt, String userPrompt) throws LlmException {
            prompts.add(userPrompt);
            if (replies.isEmpty()) {
                throw new LlmException("no more replies");
            }
            return new LlmResponse(replies.pop(), "fake", 100, 50, 0.01);
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public String model() {
            return "fake";
        }
    }

    /** Scripted build: test runs and PIT verifications succeed or fail per the queues. */
    static class FakeBuild extends BuildExecutor {
        final Deque<Boolean> testOutcomes = new ArrayDeque<>();
        final Deque<String> pitStatuses = new ArrayDeque<>();
        final List<String> testRunsSeen = new ArrayList<>();
        final List<String> pitTargets = new ArrayList<>();
        final List<String> testFileContentAtRun = new ArrayList<>();
        final Path testFile;

        FakeBuild(Path projectDir, Path testFile) {
            super(projectDir, Duration.ofMinutes(1), false, null);
            this.testFile = testFile;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public String prepare() {
            return null;
        }

        @Override
        public void cleanup() {
        }

        @Override
        public List<File> runMutationTesting(String targetClasses, String targetTests) throws BuildException {
            pitTargets.add(targetClasses + "|" + targetTests);
            String status = pitStatuses.isEmpty() ? "KILLED" : pitStatuses.pop();
            try {
                Path report = Files.createTempFile(projectDir, "mutations", ".xml");
                Files.writeString(report, "<mutations><mutation detected='false' status='" + status + "' numberOfTestsRun='1'>"
                    + "<sourceFile>Calc.java</sourceFile><mutatedClass>com.example.Calc</mutatedClass><mutatedMethod>add</mutatedMethod>"
                    + "<methodDescription>(II)I</methodDescription><lineNumber>4</lineNumber>"
                    + "<mutator>org.pitest.mutationtest.engine.gregor.mutators.MathMutator</mutator><indexes><index>1</index></indexes>"
                    + "<blocks><block>0</block></blocks><killingTest/><description>Replaced integer addition with subtraction</description>"
                    + "</mutation></mutations>");
                return List.of(report.toFile());
            } catch (Exception e) {
                throw new BuildException("boom", e);
            }
        }

        @Override
        public BuildResult runTests(String testClassFqn) {
            testRunsSeen.add(testClassFqn);
            try {
                testFileContentAtRun.add(Files.readString(testFile));
            } catch (Exception e) {
                testFileContentAtRun.add("<missing>");
            }
            boolean ok = testOutcomes.isEmpty() || testOutcomes.pop();
            return new BuildResult(ok ? 0 : 1, ok ? "BUILD SUCCESS" : "[ERROR] cannot find symbol\n  symbol: method nope()", null);
        }

        @Override
        public TestFramework detectTestFramework() {
            return TestFramework.JUNIT5;
        }
    }

    FakeLlm llm;
    FakeBuild build;
    MutantKillerConfig config;
    KillLoop loop;

    @BeforeEach
    void setUp() throws Exception {
        srcRoot = project.resolve("src/main/java");
        testRoot = project.resolve("src/test/java");
        Files.createDirectories(srcRoot.resolve("com/example"));
        Files.createDirectories(testRoot.resolve("com/example"));
        Files.writeString(srcRoot.resolve("com/example/Calc.java"),
            "package com.example;\n\npublic class Calc {\n    public int add(int a, int b) {\n        return a + b;\n    }\n}\n");
        testFile = testRoot.resolve("com/example/CalcTest.java");
        Files.writeString(testFile, "package com.example;\n\nimport org.junit.jupiter.api.Test;\n\nclass CalcTest {\n}\n");

        llm = new FakeLlm();
        build = new FakeBuild(project, testFile);
        config = MutantKillerConfig.builder().maxAttempts(3).verify(true).backend("cli").build();
        MutantAnalyzer analyzer = new MutantAnalyzer(List.of(srcRoot), List.of(testRoot), TestFramework.JUNIT5);
        loop = new KillLoop(config, build, analyzer, new TestImprover(config, llm), null);
    }

    private static final String GOOD = "```java\nimport static org.junit.jupiter.api.Assertions.assertEquals;\n\n@Test\nvoid addsTwoNumbers() {\n    assertEquals(3, new Calc().add(1, 2));\n}\n```";

    @Test
    void succeedsFirstTryAndKeepsTheTest() throws Exception {
        llm.replies.add(GOOD);
        KillResult r = loop.kill(MUTANT);

        assertEquals(KillResult.Status.KILLED_VERIFIED, r.status());
        assertEquals(1, r.attempts());
        assertEquals(testFile, r.testFile());
        assertEquals("com.example.CalcTest", r.testClassFqn());
        assertEquals(List.of("com.example.CalcTest"), build.testRunsSeen);
        assertEquals(List.of("com.example.Calc*|com.example.CalcTest"), build.pitTargets);
        String content = Files.readString(testFile);
        assertTrue(content.contains("void addsTwoNumbers()"), content);
        assertTrue(content.contains("import static org.junit.jupiter.api.Assertions.assertEquals;"), content);
        assertEquals(100, r.inputTokens());
        assertEquals(0.01, r.costUsd(), 1e-9);
        assertTrue(r.history().isEmpty());
    }

    @Test
    void retriesAfterCompileFailureWithFeedbackAndRevertsBetweenAttempts() throws Exception {
        llm.replies.add("```java\n@Test\nvoid broken() { nope(); }\n```");
        llm.replies.add(GOOD);
        build.testOutcomes.add(false);
        build.testOutcomes.add(true);

        KillResult r = loop.kill(MUTANT);

        assertEquals(KillResult.Status.KILLED_VERIFIED, r.status());
        assertEquals(2, r.attempts());
        assertEquals(1, r.history().size());
        assertTrue(r.history().get(0).problem().contains("cannot find symbol"));
        // second prompt carries the feedback
        assertTrue(llm.prompts.get(1).contains("Previous attempts that did NOT work"), llm.prompts.get(1));
        assertTrue(llm.prompts.get(1).contains("nope()"));
        assertFalse(llm.prompts.get(0).contains("Previous attempts"));
        // the broken draft was reverted before the second attempt was applied
        assertTrue(build.testFileContentAtRun.get(0).contains("broken()"));
        assertFalse(build.testFileContentAtRun.get(1).contains("broken()"));
        assertFalse(Files.readString(testFile).contains("broken()"));
    }

    @Test
    void retriesWhenPitSaysMutantStillSurvives() throws Exception {
        llm.replies.add(GOOD);
        llm.replies.add(GOOD.replace("addsTwoNumbers", "addsBoundary"));
        build.pitStatuses.add("SURVIVED");
        build.pitStatuses.add("KILLED");

        KillResult r = loop.kill(MUTANT);
        assertEquals(KillResult.Status.KILLED_VERIFIED, r.status());
        assertEquals(2, r.attempts());
        assertTrue(r.history().get(0).problem().contains("still reports this mutant as [SURVIVED]"), r.history().get(0).problem());
        String content = Files.readString(testFile);
        assertTrue(content.contains("addsBoundary"));
        assertFalse(content.contains("addsTwoNumbers"));
    }

    @Test
    void givesUpAfterMaxAttemptsAndLeavesFileUntouched() throws Exception {
        String original = Files.readString(testFile);
        llm.replies.add(GOOD);
        llm.replies.add(GOOD);
        llm.replies.add(GOOD);
        build.testOutcomes.addAll(List.of(false, false, false));

        KillResult r = loop.kill(MUTANT);
        assertEquals(KillResult.Status.FAILED, r.status());
        assertEquals(3, r.attempts());
        assertEquals(3, r.history().size());
        assertNull(r.testFile());
        assertEquals(original, Files.readString(testFile));
        assertEquals(300, r.inputTokens());
    }

    @Test
    void emptyReplyCountsAsAttempt() throws Exception {
        llm.replies.add("Sorry, no.");
        llm.replies.add(GOOD);
        KillResult r = loop.kill(MUTANT);
        assertEquals(KillResult.Status.KILLED_VERIFIED, r.status());
        assertEquals(2, r.attempts());
        assertTrue(r.history().get(0).problem().contains("did not contain a Java code block"));
    }

    @Test
    void equivalentVerdictStopsEarly() throws Exception {
        String original = Files.readString(testFile);
        llm.replies.add("**EQUIVALENT**: the extra `==` case assigns the same value, so behaviour is identical.");
        llm.replies.add(GOOD);
        KillResult r = loop.kill(MUTANT);
        assertEquals(KillResult.Status.EQUIVALENT, r.status());
        assertEquals(1, r.attempts());
        assertTrue(r.message().contains("assigns the same value"), r.message());
        assertEquals(original, Files.readString(testFile));
        assertTrue(build.testRunsSeen.isEmpty());
        assertFalse(r.success());
    }

    @Test
    void equivalentWordInsideCodeIsNotAVerdict() {
        assertFalse(KillLoop.isEquivalentVerdict("```java\n// EQUIVALENT check\n@Test void x() {}\n```"));
        assertFalse(KillLoop.isEquivalentVerdict("This is not equivalent.\n```java\nvoid x() {}\n```"));
        assertTrue(KillLoop.isEquivalentVerdict("EQUIVALENT - no behaviour change"));
        assertTrue(KillLoop.isEquivalentVerdict("  equivalent: same result"));
        assertFalse(KillLoop.isEquivalentVerdict(null));
        assertEquals(KillResult.Status.UNTESTABLE, KillLoop.verdictOf("UNTESTABLE: would need a 1GB input"));
        assertNull(KillLoop.verdictOf("```java\nvoid x() {}\n```"));
    }

    @Test
    void untestableVerdictStopsEarly() {
        llm.replies.add("UNTESTABLE: only observable through a 2^30-char input.");
        KillResult r = loop.kill(MUTANT);
        assertEquals(KillResult.Status.UNTESTABLE, r.status());
        assertTrue(r.message().contains("2^30"), r.message());
        assertTrue(build.testRunsSeen.isEmpty());
    }

    @Test
    void modelFailureIsAnError() {
        KillResult r = loop.kill(MUTANT);
        assertEquals(KillResult.Status.ERROR, r.status());
        assertTrue(r.message().contains("no more replies"));
    }

    @Test
    void missingSourceIsAnError() {
        MutationResult other = new MutationResult("com.example.Nope", "f", "()V", 1, "m", "d", "SURVIVED", null, null, 0, 0);
        KillResult r = loop.kill(other);
        assertEquals(KillResult.Status.ERROR, r.status());
        assertEquals(0, r.attempts());
    }

    @Test
    void createsNewTestFileWhenNoneExists() throws Exception {
        Files.delete(testFile);
        llm.replies.add(GOOD);
        KillResult r = loop.kill(MUTANT);
        assertEquals(KillResult.Status.KILLED_VERIFIED, r.status());
        String content = Files.readString(testFile);
        assertTrue(content.startsWith("package com.example;\n\nimport org.junit.jupiter.api.Test;"), content);
        assertTrue(content.contains("public class CalcTest {"), content);
        assertTrue(content.contains("addsTwoNumbers"), content);
    }

    @Test
    void unverifiedModeSkipsPit() throws Exception {
        config = MutantKillerConfig.builder().maxAttempts(1).verify(false).backend("cli").build();
        MutantAnalyzer analyzer = new MutantAnalyzer(List.of(srcRoot), List.of(testRoot), TestFramework.JUNIT5);
        loop = new KillLoop(config, build, analyzer, new TestImprover(config, llm), null);
        llm.replies.add(GOOD);
        KillResult r = loop.kill(MUTANT);
        assertEquals(KillResult.Status.TESTS_PASS_UNVERIFIED, r.status());
        assertTrue(build.pitTargets.isEmpty());
    }
}
