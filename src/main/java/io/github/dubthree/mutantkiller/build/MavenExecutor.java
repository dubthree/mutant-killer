package io.github.dubthree.mutantkiller.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven build executor.
 */
public class MavenExecutor extends BuildExecutor {

    private final MavenPitInjector injector;

    MavenExecutor(Path projectDir, Duration timeout, boolean verbose, Path logDir) {
        super(projectDir, timeout, verbose, logDir);
        this.injector = new MavenPitInjector(projectDir.resolve("pom.xml"));
    }

    @Override
    public String name() {
        return "Maven";
    }

    @Override
    public String prepare() throws IOException {
        if (injector.inject(detectTestFramework())) {
            return "PIT plugin (" + MavenPitInjector.PIT_VERSION + ") temporarily added to pom.xml";
        }
        return null;
    }

    @Override
    public void cleanup() {
        injector.restore();
    }

    private String mvn() {
        Path wrapper = projectDir.resolve(File.separatorChar == '\\' ? "mvnw.cmd" : "mvnw");
        if (Files.exists(wrapper)) {
            if (!Files.isExecutable(wrapper)) {
                wrapper.toFile().setExecutable(true);
            }
            return wrapper.toString();
        }
        return "mvn";
    }

    @Override
    public List<File> runMutationTesting(String targetClasses, String targetTests)
            throws BuildException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(mvn());
        command.add("-B");
        command.add("test-compile");
        command.add("org.pitest:pitest-maven:mutationCoverage");
        command.add("-DoutputFormats=XML");
        command.add("-DtimestampedReports=false");
        command.add("-DfailWhenNoMutations=false");
        command.add("-Dthreads=" + threads());
        if (targetClasses != null && !targetClasses.isBlank()) {
            command.add("-DtargetClasses=" + targetClasses);
        }
        if (targetTests != null && !targetTests.isBlank()) {
            command.add("-DtargetTests=" + targetTests);
        }
        command.addAll(mavenSkipFlags());

        // Stale reports from a previous run must not be mistaken for this run's output.
        for (File old : findReports(projectDir)) {
            old.delete();
        }

        BuildResult result = execute(command, "pitest");
        if (!result.success()) {
            throw new BuildException("Maven PIT run failed (exit " + result.exitCode() + ")"
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
        String simple = testClassFqn.substring(testClassFqn.lastIndexOf('.') + 1);
        List<String> command = new ArrayList<>();
        command.add(mvn());
        command.add("-B");
        command.add("test");
        command.add("-Dtest=" + simple);
        command.add("-Dsurefire.failIfNoSpecifiedTests=false");
        command.add("-DfailIfNoTests=false");
        command.addAll(mavenSkipFlags());
        return execute(command, "test-" + simple);
    }

    @Override
    public TestFramework detectTestFramework() {
        return TestFramework.fromBuildFile(readBuildFiles("pom.xml"));
    }
}
