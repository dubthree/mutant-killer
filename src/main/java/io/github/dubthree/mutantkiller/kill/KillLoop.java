package io.github.dubthree.mutantkiller.kill;

import io.github.dubthree.mutantkiller.analysis.MutantAnalysis;
import io.github.dubthree.mutantkiller.analysis.MutantAnalyzer;
import io.github.dubthree.mutantkiller.build.BuildException;
import io.github.dubthree.mutantkiller.build.BuildExecutor;
import io.github.dubthree.mutantkiller.build.BuildResult;
import io.github.dubthree.mutantkiller.codegen.GeneratedTest;
import io.github.dubthree.mutantkiller.codegen.TestEdit;
import io.github.dubthree.mutantkiller.codegen.TestImprover;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import io.github.dubthree.mutantkiller.llm.LlmException;
import io.github.dubthree.mutantkiller.llm.LlmResponse;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import io.github.dubthree.mutantkiller.pit.PitReportParser;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The agent loop for one mutant: ask the model for a test, apply it, compile and run it,
 * re-run PIT on the mutated class to prove the mutant is dead, and feed any failure back to
 * the model for another try.
 */
public class KillLoop {

    private final MutantKillerConfig config;
    private final BuildExecutor build;
    private final MutantAnalyzer analyzer;
    private final TestImprover improver;
    private final PitReportParser parser = new PitReportParser();
    private final Consumer<String> log;

    public KillLoop(MutantKillerConfig config, BuildExecutor build, MutantAnalyzer analyzer,
                    TestImprover improver, Consumer<String> log) {
        this.config = config;
        this.build = build;
        this.analyzer = analyzer;
        this.improver = improver;
        this.log = log == null ? s -> { } : log;
    }

    public KillResult kill(MutationResult mutant) {
        Instant start = Instant.now();
        long inTokens = 0;
        long outTokens = 0;
        double cost = 0;
        boolean costKnown = true;
        List<TestImprover.Attempt> history = new ArrayList<>();

        MutantAnalysis analysis;
        try {
            analysis = analyzer.analyze(mutant);
        } catch (IOException e) {
            return new KillResult(mutant, KillResult.Status.ERROR, 0, null, null, null,
                e.getMessage(), history, 0, 0, 0, Duration.between(start, Instant.now()));
        }
        log.accept("Test class: " + analysis.testClassFqn()
            + (analysis.hasExistingTest() ? " (existing)" : " (new file)")
            + ", framework: " + analysis.testFramework().name());

        String lastCode = null;
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            log.accept("Attempt " + attempt + "/" + config.maxAttempts() + ": asking " + improver.getClass().getSimpleName().replace("TestImprover", "the model") + "...");
            LlmResponse response;
            try {
                response = improver.generate(analysis, history);
            } catch (LlmException e) {
                return new KillResult(mutant, KillResult.Status.ERROR, attempt, null, analysis.testClassFqn(), lastCode,
                    "Model call failed: " + e.getMessage(), history, inTokens, outTokens,
                    costKnown ? cost : Double.NaN, Duration.between(start, Instant.now()));
            }
            inTokens += response.inputTokens();
            outTokens += response.outputTokens();
            if (response.hasCost()) {
                cost += response.costUsd();
            } else {
                costKnown = false;
            }
            log.accept(String.format("  model replied (%d in / %d out tokens%s)",
                response.inputTokens(), response.outputTokens(),
                response.hasCost() ? String.format(", $%.4f", response.costUsd()) : ""));

            GeneratedTest generated = GeneratedTest.parse(response.text());
            lastCode = generated.rawCode();
            if (generated.isEmpty()) {
                history.add(new TestImprover.Attempt(attempt, response.text(),
                    "The reply did not contain a Java code block with test methods."));
                log.accept("  no code in reply");
                continue;
            }
            log.accept("  generated members: " + generated.methodNames());

            TestEdit edit;
            try {
                edit = TestEdit.apply(analysis, generated);
            } catch (IOException e) {
                history.add(new TestImprover.Attempt(attempt, generated.rawCode(),
                    "Could not write the test file: " + e.getMessage()));
                continue;
            }

            String problem = null;
            try {
                log.accept("  compiling and running " + analysis.testSimpleName() + "...");
                BuildResult testRun = build.runTests(analysis.testClassFqn());
                if (!testRun.success()) {
                    problem = "The test class did not compile or its tests failed. Build output:\n"
                        + testRun.errorSummary(60);
                    log.accept("  build/test FAILED" + (testRun.logFile() != null ? " (log: " + testRun.logFile() + ")" : ""));
                } else if (config.verify()) {
                    log.accept("  tests pass; re-running PIT on " + mutant.outerClass() + " to verify...");
                    String verdict = verify(mutant, analysis);
                    if (verdict != null) {
                        problem = verdict;
                        log.accept("  verification FAILED: " + firstLine(verdict));
                    } else {
                        log.accept("  mutant KILLED (verified by PIT)");
                    }
                } else {
                    log.accept("  tests pass (PIT verification disabled)");
                }
            } catch (BuildException e) {
                problem = "Build tool failure: " + e.getMessage();
                log.accept("  build error: " + firstLine(e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                revertQuietly(edit);
                return new KillResult(mutant, KillResult.Status.ERROR, attempt, null, analysis.testClassFqn(), lastCode,
                    "Interrupted", history, inTokens, outTokens, costKnown ? cost : Double.NaN,
                    Duration.between(start, Instant.now()));
            }

            if (problem == null) {
                KillResult.Status status = config.verify()
                    ? KillResult.Status.KILLED_VERIFIED : KillResult.Status.TESTS_PASS_UNVERIFIED;
                return new KillResult(mutant, status, attempt, edit.file(), analysis.testClassFqn(),
                    generated.rawCode(), status == KillResult.Status.KILLED_VERIFIED
                        ? "Mutant killed; verified with a scoped PIT run" : "Tests pass; PIT verification skipped",
                    history, inTokens, outTokens, costKnown ? cost : Double.NaN,
                    Duration.between(start, Instant.now()));
            }

            revertQuietly(edit);
            history.add(new TestImprover.Attempt(attempt, generated.rawCode(), problem));
        }

        return new KillResult(mutant, KillResult.Status.FAILED, config.maxAttempts(), null,
            analysis.testClassFqn(), lastCode,
            "Gave up after " + config.maxAttempts() + " attempt(s): " + firstLine(history.get(history.size() - 1).problem()),
            history, inTokens, outTokens, costKnown ? cost : Double.NaN, Duration.between(start, Instant.now()));
    }

    /**
     * Re-run PIT restricted to the mutated class and the test class.
     *
     * @return null when the mutant is now detected, otherwise a description of the problem
     */
    private String verify(MutationResult mutant, MutantAnalysis analysis)
            throws BuildException, InterruptedException {
        String targetClasses = mutant.outerClass() + "*";
        List<File> reports = build.runMutationTesting(targetClasses, analysis.testClassFqn());
        List<MutationResult> results;
        try {
            results = parser.parseAll(reports);
        } catch (IOException e) {
            throw new BuildException("Could not parse PIT verification report: " + e.getMessage(), e);
        }

        List<MutationResult> exact = results.stream().filter(mutant::sameMutant).toList();
        List<MutationResult> matches = exact;
        if (matches.isEmpty()) {
            // Mutant indexes can shift between PIT configurations; fall back to location + mutator.
            matches = results.stream()
                .filter(r -> r.mutatedClass().equals(mutant.mutatedClass())
                    && r.mutatedMethod().equals(mutant.mutatedMethod())
                    && r.lineNumber() == mutant.lineNumber()
                    && java.util.Objects.equals(r.mutator(), mutant.mutator())
                    && java.util.Objects.equals(r.description(), mutant.description()))
                .toList();
        }
        if (matches.isEmpty()) {
            return "The verification PIT run (targetClasses=" + targetClasses + ", targetTests="
                + analysis.testClassFqn() + ") did not contain this mutant at all, so it could not be "
                + "confirmed dead. " + results.size() + " mutants were reported.";
        }
        boolean allDetected = matches.stream().allMatch(MutationResult::isDetected);
        if (allDetected) {
            return null;
        }
        String statuses = matches.stream().map(MutationResult::status).distinct().toList().toString();
        return "The test compiles and passes, but PIT still reports this mutant as " + statuses
            + " when running " + analysis.testClassFqn() + " against it. The new test does not "
            + "exercise the mutated behaviour (line " + mutant.lineNumber() + ": "
            + mutant.getMutatorDescription() + ") with an assertion that would fail under the mutation. "
            + "Write a test whose assertion depends on exactly that behaviour.";
    }

    private void revertQuietly(TestEdit edit) {
        try {
            edit.revert();
        } catch (IOException e) {
            log.accept("  warning: could not revert " + edit.file() + ": " + e.getMessage());
        }
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }
}
