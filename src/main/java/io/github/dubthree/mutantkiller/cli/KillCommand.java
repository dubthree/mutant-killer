package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.analysis.MutantAnalyzer;
import io.github.dubthree.mutantkiller.codegen.TestImprover;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import static io.github.dubthree.mutantkiller.config.MutantKillerConfig.DEFAULT_MODEL;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import io.github.dubthree.mutantkiller.pit.PitReportParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The main command - analyzes surviving mutants and generates test improvements.
 */
@Command(
    name = "kill",
    description = "Analyze surviving mutants and generate improved tests to kill them"
)
public class KillCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to PIT mutations.xml report")
    private File reportFile;

    @Option(names = {"-s", "--source"}, description = "Source directory", required = true)
    private File sourceDir;

    @Option(names = {"-t", "--test"}, description = "Test source directory", required = true)
    private File testDir;

    @Option(names = {"--model"}, description = "LLM model to use", defaultValue = DEFAULT_MODEL)
    private String model;

    @Option(names = {"--dry-run"}, description = "Show proposed changes without applying")
    private boolean dryRun;

    @Option(names = {"--max-mutants"}, description = "Maximum number of mutants to process", defaultValue = "10")
    private int maxMutants;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        // Validate inputs
        if (!reportFile.exists()) {
            System.err.println("Report file not found: " + reportFile);
            return 1;
        }
        if (!sourceDir.isDirectory()) {
            System.err.println("Source directory not found: " + sourceDir);
            return 1;
        }
        if (!testDir.isDirectory()) {
            System.err.println("Test directory not found: " + testDir);
            return 1;
        }

        // Load config
        MutantKillerConfig config = MutantKillerConfig.builder()
            .model(model)
            .sourceDir(sourceDir.toPath())
            .testDir(testDir.toPath())
            .dryRun(dryRun)
            .verbose(verbose)
            .build();

        // Parse report
        System.out.println("Parsing PIT report...");
        PitReportParser parser = new PitReportParser();
        List<MutationResult> mutations = parser.parse(reportFile);

        List<MutationResult> survived = mutations.stream()
            .filter(MutationResult::survived)
            .limit(maxMutants)
            .toList();

        if (survived.isEmpty()) {
            System.out.println("No surviving mutants found. Your tests are strong!");
            return 0;
        }

        System.out.println("Found " + survived.size() + " surviving mutants to kill.");
        System.out.println();

        // Analyze and improve
        MutantAnalyzer analyzer = new MutantAnalyzer(config);
        TestImprover improver = new TestImprover(config);

        int killed = 0;
        for (MutationResult mutant : survived) {
            System.out.println("=== Processing mutant in " + mutant.mutatedClass() + " ===");
            System.out.println("Method: " + mutant.mutatedMethod() + " (line " + mutant.lineNumber() + ")");
            System.out.println("Mutator: " + mutant.mutator());
            
            try {
                var analysis = analyzer.analyze(mutant);
                var improvement = improver.improve(mutant, analysis);
                
                if (improvement.isPresent()) {
                    if (dryRun) {
                        System.out.println("Proposed change:");
                        System.out.println(improvement.get().diff());
                    } else {
                        improvement.get().apply();
                        System.out.println("Applied test improvement.");
                    }
                    killed++;
                } else {
                    System.out.println("Could not generate improvement for this mutant.");
                }
            } catch (Exception e) {
                System.err.println("Error processing mutant: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
            }
            System.out.println();
        }

        System.out.println("=== Summary ===");
        System.out.println("Processed: " + survived.size() + " mutants");
        System.out.println("Improvements: " + killed);
        if (dryRun) {
            System.out.println("(dry run - no changes applied)");
        }

        return 0;
    }
}
