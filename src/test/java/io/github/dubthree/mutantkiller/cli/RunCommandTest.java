package io.github.dubthree.mutantkiller.cli;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RunCommand helper methods via reflection.
 */
class RunCommandTest {

    private final RunCommand cmd = new RunCommand();

    private String invokePrivate(String methodName, Object... args) throws Exception {
        Class<?>[] types = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        Method m = RunCommand.class.getDeclaredMethod(methodName, types);
        m.setAccessible(true);
        return (String) m.invoke(cmd, args);
    }

    // --- extractRepoName ---

    @Test
    void extractRepoName_httpsUrl() throws Exception {
        assertEquals("repo", invokePrivate("extractRepoName", "https://github.com/owner/repo"));
    }

    @Test
    void extractRepoName_withGitSuffix() throws Exception {
        assertEquals("repo", invokePrivate("extractRepoName", "https://github.com/owner/repo.git"));
    }

    @Test
    void extractRepoName_sshUrl() throws Exception {
        assertEquals("repo", invokePrivate("extractRepoName", "git@github.com:owner/repo.git"));
    }

    @Test
    void extractRepoName_simpleName() throws Exception {
        assertEquals("repo", invokePrivate("extractRepoName", "repo"));
    }

    // --- simpleClassName ---

    @Test
    void simpleClassName_fullyQualified() throws Exception {
        assertEquals("Foo", invokePrivate("simpleClassName", "com.example.Foo"));
    }

    @Test
    void simpleClassName_noPackage() throws Exception {
        assertEquals("Foo", invokePrivate("simpleClassName", "Foo"));
    }

    @Test
    void simpleClassName_deepPackage() throws Exception {
        assertEquals("Bar", invokePrivate("simpleClassName", "a.b.c.d.Bar"));
    }

    // --- generateMutantId ---

    @Test
    void generateMutantId() throws Exception {
        var mutation = new io.github.dubthree.mutantkiller.pit.MutationResult(
            "com.example.Foo", "doStuff", "(I)V", 42,
            "m", "d", "SURVIVED", "Foo.java", null);

        Method m = RunCommand.class.getDeclaredMethod("generateMutantId",
            io.github.dubthree.mutantkiller.pit.MutationResult.class, int.class);
        m.setAccessible(true);
        String id = (String) m.invoke(cmd, mutation, 3);

        assertEquals("foo-dostuff-42-3", id);
    }

    // --- buildPrBody ---

    @Test
    void buildPrBody_containsDetails() throws Exception {
        var mutation = new io.github.dubthree.mutantkiller.pit.MutationResult(
            "com.example.Foo", "bar", "(I)V", 10,
            "org.pitest.mutationtest.engine.gregor.mutators.MathMutator",
            "desc", "SURVIVED", "Foo.java", null);
        var analysis = new io.github.dubthree.mutantkiller.analysis.MutantAnalysis(
            mutation, java.nio.file.Path.of("Foo.java"), "src", "method", "ctx", null, null);
        var improvement = new io.github.dubthree.mutantkiller.codegen.TestImprovement(
            analysis, "@Test void t() {}", true);

        Method m = RunCommand.class.getDeclaredMethod("buildPrBody",
            io.github.dubthree.mutantkiller.pit.MutationResult.class,
            io.github.dubthree.mutantkiller.analysis.MutantAnalysis.class,
            io.github.dubthree.mutantkiller.codegen.TestImprovement.class);
        m.setAccessible(true);
        String body = (String) m.invoke(cmd, mutation, analysis, improvement);

        assertTrue(body.contains("com.example.Foo"));
        assertTrue(body.contains("bar"));
        assertTrue(body.contains("10"));
        assertTrue(body.contains("@Test void t() {}"));
        assertTrue(body.contains("mutant-killer"));
    }
}
