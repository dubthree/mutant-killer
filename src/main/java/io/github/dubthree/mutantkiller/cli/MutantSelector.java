package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.pit.MutationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chooses which surviving mutants to work on.
 */
final class MutantSelector {

    private MutantSelector() {
    }

    /**
     * Surviving mutants, spread across classes (round-robin by class in report order) so a small
     * budget samples the whole project instead of the first class in the report.
     */
    static List<MutationResult> select(List<MutationResult> all, boolean includeNoCoverage, int max) {
        Map<String, List<MutationResult>> byClass = new LinkedHashMap<>();
        for (MutationResult m : all) {
            if (m.isSurvived() || (includeNoCoverage && m.isNoCoverage())) {
                byClass.computeIfAbsent(m.outerClass(), k -> new ArrayList<>()).add(m);
            }
        }
        List<MutationResult> selected = new ArrayList<>();
        boolean added = true;
        int round = 0;
        while (added && selected.size() < max) {
            added = false;
            for (List<MutationResult> list : byClass.values()) {
                if (round < list.size() && selected.size() < max) {
                    selected.add(list.get(round));
                    added = true;
                }
            }
            round++;
        }
        return selected;
    }

    static long count(List<MutationResult> all, String status) {
        return all.stream().filter(m -> status.equals(m.status())).count();
    }
}
