package io.github.dubthree.mutantkiller.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Gradle build executor. PIT is wired in through an init script, which both applies the
 * gradle-pitest-plugin to projects that lack it and lets us scope a run with
 * {@code -PmkTargetClasses} / {@code -PmkTargetTests} without touching the project's build files.
 */
public class GradleExecutor extends BuildExecutor {

    public static final String GRADLE_PITEST_PLUGIN_VERSION = "1.19.0";

    private Path initScript;

    GradleExecutor(Path projectDir, Duration timeout, boolean verbose, Path logDir) {
        super(projectDir, timeout, verbose, logDir);
    }

    @Override
    public String name() {
        return "Gradle";
    }

    @Override
    public String prepare() throws IOException {
        String template;
        try (var in = getClass().getResourceAsStream("/gradle/mutant-killer-pitest.init.gradle")) {
            if (in == null) {
                throw new IOException("Bundled Gradle init script is missing");
            }
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        TestFramework fw = detectTestFramework();
        String script = template
            .replace("@PLUGIN_VERSION@", GRADLE_PITEST_PLUGIN_VERSION)
            .replace("@PIT_VERSION@", MavenPitInjector.PIT_VERSION)
            .replace("@JUNIT5_PLUGIN_VERSION@", fw == TestFramework.JUNIT5 ? MavenPitInjector.JUNIT5_PLUGIN_VERSION : "")
            .replace("@THREADS@", String.valueOf(threads()));
        Path dir = logDir != null ? logDir : Files.createTempDirectory("mutant-killer-gradle");
        Files.createDirectories(dir);
        initScript = dir.resolve("mutant-killer-pitest.init.gradle");
        Files.writeString(initScript, script, StandardCharsets.UTF_8);
        return "PIT applied through Gradle init script " + initScript;
    }

    @Override
    public void cleanup() {
        // Nothing in the project tree was modified.
    }

    private String gradle() {
        Path wrapper = projectDir.resolve(File.separatorChar == '\\' ? "gradlew.bat" : "gradlew");
        if (Files.exists(wrapper)) {
            if (!Files.isExecutable(wrapper)) {
                wrapper.toFile().setExecutable(true);
            }
            return wrapper.toString();
        }
        return "gradle";
    }

    private List<String> baseCommand() {
        List<String> command = new ArrayList<>();
        command.add(gradle());
        command.add("--console=plain");
        command.add("--stacktrace");
        if (initScript != null) {
            command.add("--init-script");
            command.add(initScript.toString());
        }
        return command;
    }

    @Override
    public List<File> runMutationTesting(String targetClasses, String targetTests)
            throws BuildException, InterruptedException {
        if (initScript == null) {
            throw new BuildException("prepare() must be called before running PIT with Gradle");
        }
        List<String> command = baseCommand();
        command.add("pitest");
        if (targetClasses != null && !targetClasses.isBlank()) {
            command.add("-PmkTargetClasses=" + targetClasses);
        }
        if (targetTests != null && !targetTests.isBlank()) {
            command.add("-PmkTargetTests=" + targetTests);
        }

        for (File old : findReports(projectDir)) {
            old.delete();
        }

        BuildResult result = execute(command, "pitest");
        if (!result.success()) {
            throw new BuildException("Gradle PIT run failed (exit " + result.exitCode() + ")"
                + (result.logFile() != null ? ", see " + result.logFile() : "")
                + "\n" + result.tail(40), result);
        }
        List<File> reports = findReports(projectDir);
        if (reports.isEmpty()) {
            throw new BuildException("PIT finished but produced no mutations.xml"
                + (result.logFile() != null ? ", see " + result.logFile() : ""), result);
        }
        return reports;
    }

    @Override
    public BuildResult runTests(String testClassFqn) throws BuildException, InterruptedException {
        List<String> command = baseCommand();
        command.add("test");
        command.add("--tests");
        command.add(testClassFqn);
        return execute(command, "test-" + testClassFqn.substring(testClassFqn.lastIndexOf('.') + 1));
    }

    @Override
    public TestFramework detectTestFramework() {
        return TestFramework.fromBuildFile(readBuildFiles("build.gradle", "build.gradle.kts", "libs.versions.toml"));
    }
}
