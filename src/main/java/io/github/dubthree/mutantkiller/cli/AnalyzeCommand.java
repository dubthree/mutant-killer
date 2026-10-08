package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.pit.MutationResult;
import io.github.dubthree.mutantkiller.pit.PitReportParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

/**
 * Prints the mutants in a PIT report.
 */
@Command(
    name = "analyze",
    description = "Analyze a PIT mutations.xml report and list surviving mutants"
)
public class AnalyzeCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "1..*", description = "Path(s) to PIT mutations.xml report(s)")
    private List<File> reportFiles;

    @Option(names = {"-v", "--verbose"}, description = "Show detailed mutation info")
    private boolean verbose;

    @Option(names = {"--all"}, description = "Show all mutants, not just survivors")
    private boolean all;

    @Override
    public Integer call() throws Exception {
        for (File f : reportFiles) {
            if (!f.exists()) {
                System.err.println("Report file not found: " + f);
                return 1;
            }
        }

        List<MutationResult> mutations = new PitReportParser().parseAll(reportFiles);
        List<MutationResult> toShow = all ? mutations : mutations.stream().filter(MutationResult::survived).toList();

        Map<String, Long> byStatus = new TreeMap<>();
        mutations.forEach(m -> byStatus.merge(m.status(), 1L, Long::sum));

        System.out.println("=== Mutation Analysis ===");
        System.out.println("Total mutations: " + mutations.size());
        byStatus.forEach((status, count) -> System.out.printf("  %-14s %d%n", status, count));
        long detected = mutations.stream().filter(MutationResult::isDetected).count();
        System.out.printf("Mutation score: %.1f%%%n", 100.0 * detected / Math.max(1, mutations.size()));
        System.out.println();

        if (!toShow.isEmpty()) {
            System.out.println("=== " + (all ? "All" : "Surviving") + " Mutants (" + toShow.size() + ") ===");
            for (MutationResult mutation : toShow) {
                printMutation(mutation);
            }
        }
        return 0;
    }

    private void printMutation(MutationResult mutation) {
        System.out.println("---");
        System.out.println("Class:    " + mutation.mutatedClass());
        System.out.println("Method:   " + mutation.mutatedMethod());
        System.out.println("Line:     " + mutation.lineNumber());
        System.out.println("Mutation: " + mutation.getMutatorDescription());
        System.out.println("Status:   " + mutation.status());
        if (verbose) {
            System.out.println("Mutator:  " + mutation.mutator());
            System.out.println("Source:   " + mutation.sourceFile());
            if (mutation.killingTest() != null) {
                System.out.println("Killed by: " + mutation.killingTest());
            }
        }
    }
}
