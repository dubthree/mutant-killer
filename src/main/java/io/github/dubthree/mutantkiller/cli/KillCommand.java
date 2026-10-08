package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.analysis.MutantAnalyzer;
import io.github.dubthree.mutantkiller.build.BuildExecutor;
import io.github.dubthree.mutantkiller.codegen.TestImprover;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import io.github.dubthree.mutantkiller.kill.KillLoop;
import io.github.dubthree.mutantkiller.kill.KillResult;
import io.github.dubthree.mutantkiller.llm.LlmClient;
import io.github.dubthree.mutantkiller.llm.LlmException;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import io.github.dubthree.mutantkiller.pit.PitReportParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Kill mutants in a local checkout, starting from an existing PIT report (or running PIT first).
 * Changes stay in the working tree for you to review and commit.
 */
@Command(
    name = "kill",
    description = "Generate and verify tests that kill surviving mutants in a local project (no git, no PRs)"
)
public class KillCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "PIT mutations.xml report. Omitted: PIT is run first.")
    private File reportFile;

    @Option(names = {"-p", "--project"}, description = "Project directory (default: current directory)", defaultValue = ".")
    private File projectDir;

    @Option(names = {"--dry-run"}, description = "Only show the generated tests; do not write, build or verify")
    private boolean dryRun;

    @Mixin
    private KillOptions options;

    @Override
    public Integer call() throws Exception {
        Path project = projectDir.toPath().toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(project)) {
            System.err.println("Project directory not found: " + project);
            return 1;
        }
        if (reportFile != null && !reportFile.exists()) {
            System.err.println("Report file not found: " + reportFile);
            return 1;
        }

        MutantKillerConfig config = options.toConfig(dryRun);
        LlmClient llm;
        try {
            llm = LlmClient.create(config);
        } catch (LlmException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        Path logDir = project.resolve("target").resolve("mutant-killer-logs");
        BuildExecutor build = BuildExecutor.detect(project, options.buildSystem, config.buildTimeout(), config.verbose(), logDir);
        if (build == null) {
            System.err.println("Could not detect a Maven or Gradle build in " + project);
            return 1;
        }

        RunReport report = new RunReport(project.toString(), llm.name(), llm.model());
        try (llm) {
            String note = build.prepare();
            if (note != null) {
                System.out.println("Note: " + note);
            }

            List<MutationResult> mutations;
            if (reportFile != null) {
                mutations = new PitReportParser().parse(reportFile);
            } else {
                System.out.println("Running PIT (" + build.name() + ")...");
                mutations = new PitReportParser().parseAll(build.runMutationTesting(options.targetClasses, options.targetTests));
            }
            report.pitStats(mutations);
            List<MutationResult> selected = MutantSelector.select(mutations, options.includeNoCoverage, options.maxMutants);
            System.out.printf("Mutants: %d total, %d survived, %d no coverage; processing %d%n",
                mutations.size(), MutantSelector.count(mutations, "SURVIVED"),
                MutantSelector.count(mutations, "NO_COVERAGE"), selected.size());
            if (selected.isEmpty()) {
                System.out.println("No surviving mutants found. Your tests are strong!");
                return 0;
            }

            MutantAnalyzer analyzer = new MutantAnalyzer(build.sourceRoots(), build.testRoots(), build.detectTestFramework());
            TestImprover improver = new TestImprover(config, llm);
            KillLoop loop = new KillLoop(config, build, analyzer, improver, line -> System.out.println("  " + line));

            int i = 0;
            for (MutationResult mutant : selected) {
                i++;
                System.out.println();
                System.out.printf("--- Mutant %d/%d: %s ---%n", i, selected.size(), mutant.humanReadable());
                if (dryRun) {
                    var analysis = analyzer.analyze(mutant);
                    var response = improver.generate(analysis, List.of());
                    var generated = io.github.dubthree.mutantkiller.codegen.GeneratedTest.parse(response.text());
                    System.out.println("Proposed addition to " + analysis.testClassFqn() + ":");
                    System.out.println(generated.rawCode());
                    continue;
                }
                KillResult result = loop.kill(mutant);
                System.out.println("  => " + result.status() + ": " + result.message());
                if (result.success()) {
                    System.out.println("  changed: " + result.testFile());
                }
                report.add(result, null);
            }
        } finally {
            build.cleanup();
        }

        if (!dryRun) {
            report.printSummary(false);
            if (options.report != null) {
                report.write(options.report);
                System.out.println("Report written to " + options.report);
            }
            System.out.println("Review the changes with `git diff` in " + project);
        }
        return 0;
    }
}
