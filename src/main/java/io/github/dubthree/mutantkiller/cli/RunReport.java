package io.github.dubthree.mutantkiller.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.dubthree.mutantkiller.kill.KillResult;
import io.github.dubthree.mutantkiller.pit.MutationResult;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects per-mutant outcomes, prints the summary and writes the optional JSON report.
 */
final class RunReport {

    private final String target;
    private final String backend;
    private final String model;
    private final Instant started = Instant.now();
    private final List<KillResult> results = new ArrayList<>();
    private final Map<String, String> extra = new LinkedHashMap<>();
    private int totalMutants;
    private int survivedMutants;
    private int noCoverageMutants;
    private int killedMutants;
    private String prUrlFor;

    RunReport(String target, String backend, String model) {
        this.target = target;
        this.backend = backend;
        this.model = model;
    }

    void pitStats(List<MutationResult> all) {
        totalMutants = all.size();
        killedMutants = (int) all.stream().filter(MutationResult::isDetected).count();
        survivedMutants = (int) all.stream().filter(MutationResult::isSurvived).count();
        noCoverageMutants = (int) all.stream().filter(MutationResult::isNoCoverage).count();
    }

    void add(KillResult result, String prUrl) {
        results.add(result);
        if (prUrl != null) {
            extra.put(key(result), prUrl);
        }
    }

    void note(String key, String value) {
        extra.put(key, value);
    }

    private static String key(KillResult r) {
        return r.mutant().mutatedClass() + "#" + r.mutant().mutatedMethod() + ":" + r.mutant().lineNumber() + "/" + r.mutant().index();
    }

    long successes() {
        return results.stream().filter(KillResult::success).count();
    }

    double costUsd() {
        double sum = 0;
        for (KillResult r : results) {
            if (Double.isNaN(r.costUsd())) {
                return Double.NaN;
            }
            sum += r.costUsd();
        }
        return sum;
    }

    long inputTokens() {
        return results.stream().mapToLong(KillResult::inputTokens).sum();
    }

    long outputTokens() {
        return results.stream().mapToLong(KillResult::outputTokens).sum();
    }

    void printSummary(boolean dryRun) {
        System.out.println();
        System.out.println("=== Summary ===");
        if (totalMutants > 0) {
            System.out.printf("PIT baseline: %d mutants, %d detected, %d survived, %d no coverage (%.0f%% mutation score)%n",
                totalMutants, killedMutants, survivedMutants, noCoverageMutants,
                100.0 * killedMutants / Math.max(1, totalMutants));
        }
        System.out.println("Mutants processed: " + results.size());
        long verified = results.stream().filter(r -> r.status() == KillResult.Status.KILLED_VERIFIED).count();
        long unverified = results.stream().filter(r -> r.status() == KillResult.Status.TESTS_PASS_UNVERIFIED).count();
        long failed = results.stream().filter(r -> r.status() == KillResult.Status.FAILED).count();
        long errors = results.stream().filter(r -> r.status() == KillResult.Status.ERROR).count();
        long equivalent = results.stream().filter(r -> r.status() == KillResult.Status.EQUIVALENT).count();
        System.out.println("Killed (verified by PIT): " + verified);
        if (unverified > 0) {
            System.out.println("Tests pass (unverified):  " + unverified);
        }
        System.out.println("Failed after retries:     " + failed);
        if (equivalent > 0) {
            System.out.println("Judged equivalent:        " + equivalent);
        }
        System.out.println("Errors:                   " + errors);
        long prs = extra.values().stream().filter(v -> v.startsWith("http")).count();
        if (!dryRun && prs > 0) {
            System.out.println("Pull requests opened:     " + prs);
        }
        double cost = costUsd();
        System.out.printf("Model usage: %d input / %d output tokens%s%n", inputTokens(), outputTokens(),
            Double.isNaN(cost) ? "" : String.format(", about $%.2f", cost));
        if (dryRun) {
            System.out.println("(dry run - nothing was committed or pushed)");
        }

        if (!results.isEmpty()) {
            System.out.println();
            System.out.println("| # | Class | Method | Line | Mutation | Attempts | Outcome |");
            System.out.println("|---|-------|--------|------|----------|----------|---------|");
            int i = 1;
            for (KillResult r : results) {
                MutationResult m = r.mutant();
                System.out.printf("| %d | %s | %s | %d | %s | %d | %s |%n", i++,
                    m.simpleOuterClassName() + (m.mutatedClass().contains("$") ? m.mutatedClass().substring(m.mutatedClass().indexOf('$')) : ""),
                    m.mutatedMethod(), m.lineNumber(), shorten(m.getMutatorDescription(), 50),
                    r.attempts(), r.status().name().toLowerCase().replace('_', ' '));
            }
        }
    }

    void write(File file) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("target", target);
        root.put("backend", backend);
        root.put("model", model);
        root.put("startedAt", started.toString());
        root.put("finishedAt", Instant.now().toString());
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("totalMutants", totalMutants);
        baseline.put("detected", killedMutants);
        baseline.put("survived", survivedMutants);
        baseline.put("noCoverage", noCoverageMutants);
        root.put("pitBaseline", baseline);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("processed", results.size());
        totals.put("succeeded", successes());
        totals.put("inputTokens", inputTokens());
        totals.put("outputTokens", outputTokens());
        totals.put("costUsd", Double.isNaN(costUsd()) ? null : costUsd());
        root.put("totals", totals);
        root.put("notes", extra);

        List<Map<String, Object>> items = new ArrayList<>();
        for (KillResult r : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            MutationResult m = r.mutant();
            item.put("mutatedClass", m.mutatedClass());
            item.put("mutatedMethod", m.mutatedMethod());
            item.put("lineNumber", m.lineNumber());
            item.put("mutator", m.mutator());
            item.put("description", m.getMutatorDescription());
            item.put("originalStatus", m.status());
            item.put("outcome", r.status().name());
            item.put("attempts", r.attempts());
            item.put("message", r.message());
            item.put("testFile", r.testFile() == null ? null : r.testFile().toString());
            item.put("testClass", r.testClassFqn());
            item.put("generatedCode", r.generatedCode());
            item.put("pullRequest", extra.get(key(r)));
            item.put("inputTokens", r.inputTokens());
            item.put("outputTokens", r.outputTokens());
            item.put("costUsd", Double.isNaN(r.costUsd()) ? null : r.costUsd());
            item.put("elapsedSeconds", r.elapsed().toSeconds());
            List<Map<String, String>> attempts = new ArrayList<>();
            r.history().forEach(a -> {
                Map<String, String> att = new LinkedHashMap<>();
                att.put("attempt", String.valueOf(a.number()));
                att.put("problem", a.problem());
                att.put("code", a.code());
                attempts.add(att);
            });
            item.put("failedAttempts", attempts);
            items.add(item);
        }
        root.put("mutants", items);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        mapper.writeValue(file, root);
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
