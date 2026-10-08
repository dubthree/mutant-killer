package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.pit.MutationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MutantSelectorTest {

    private static MutationResult m(String cls, int line, String status) {
        return new MutationResult(cls, "f", "()V", line, "m", "d", status, null, null, 0, 0);
    }

    @Test
    void roundRobinsAcrossClassesAndRespectsLimit() {
        List<MutationResult> all = List.of(
            m("a.A", 1, "SURVIVED"), m("a.A", 2, "SURVIVED"), m("a.A", 3, "SURVIVED"),
            m("a.B", 1, "SURVIVED"), m("a.B", 2, "KILLED"),
            m("a.C", 1, "NO_COVERAGE"));
        List<MutationResult> picked = MutantSelector.select(all, false, 3);
        assertEquals(List.of("a.A", "a.B", "a.A"), picked.stream().map(MutationResult::mutatedClass).toList());
        assertEquals(List.of(1, 1, 2), picked.stream().map(MutationResult::lineNumber).toList());
    }

    @Test
    void noCoverageOnlyWhenRequested() {
        List<MutationResult> all = List.of(m("a.A", 1, "NO_COVERAGE"), m("a.A$I", 2, "SURVIVED"));
        assertEquals(1, MutantSelector.select(all, false, 10).size());
        assertEquals(2, MutantSelector.select(all, true, 10).size());
        assertEquals(1, MutantSelector.count(all, "NO_COVERAGE"));
    }

    @Test
    void branchIdIsFilesystemAndGitSafe() {
        MutationResult r = new MutationResult("com.x.Foo$Bar", "<init>", "()V", 12, "m", "d", "SURVIVED", null, null, 0, 0);
        assertEquals("foo-init-12-3", RunCommand.branchId(r, 3));
    }
}
