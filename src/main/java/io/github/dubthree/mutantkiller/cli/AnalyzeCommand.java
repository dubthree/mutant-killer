package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.pit.PitReportParser;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Analyzes PIT mutation reports and displays surviving mutants.
 */
@Command(
    name = "analyze",
    description = "Analyze PIT mutation report and list surviving mutants"
)
public class AnalyzeCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to PIT mutations.xml report")
    private File reportFile;

    @Option(names = {"-v", "--verbose"}, description = "Show detailed mutation info")
    private boolean verbose;

    @Option(names = {"--survived-only"}, description = "Only show survived mutations", defaultValue = "true")
    private boolean survivedOnly;

    @Override
    public Integer call() throws Exception {
        if (!reportFile.exists()) {
            System.err.println("Report file not found: " + reportFile);
            return 1;
        }

        PitReportParser parser = new PitReportParser();
        List<MutationResult> mutations = parser.parse(reportFile);

        List<MutationResult> toShow = survivedOnly 
            ? mutations.stream().filter(MutationResult::survived).toList()
            : mutations;

        System.out.println("=== Mutation Analysis ===");
        System.out.println("Total mutations: " + mutations.size());
        System.out.println("Survived: " + mutations.stream().filter(MutationResult::survived).count());
        System.out.println("Killed: " + mutations.stream().filter(m -> !m.survived()).count());
        System.out.println();

        if (!toShow.isEmpty()) {
            System.out.println("=== " + (survivedOnly ? "Surviving" : "All") + " Mutants ===");
            for (MutationResult mutation : toShow) {
                printMutation(mutation);
            }
        }

        return 0;
    }

    private void printMutation(MutationResult mutation) {
        System.out.println("---");
        System.out.println("Class: " + mutation.mutatedClass());
        System.out.println("Method: " + mutation.mutatedMethod());
        System.out.println("Line: " + mutation.lineNumber());
        System.out.println("Mutator: " + mutation.mutator());
        if (verbose) {
            System.out.println("Description: " + mutation.description());
            System.out.println("Source file: " + mutation.sourceFile());
        }
        System.out.println("Status: " + mutation.status());
    }
}
