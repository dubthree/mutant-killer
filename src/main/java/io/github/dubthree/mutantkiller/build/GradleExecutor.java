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
        String note = "PIT applied through Gradle init script " + initScript;
        gradle();
        if (gradleNote != null) {
            note += "; " + gradleNote;
        }
        return note;
    }

    @Override
    public void cleanup() {
        // Nothing in the project tree was modified.
    }

    private String gradleNote;

    /**
     * Prefer the project's wrapper, unless it pins a Gradle too old for the running JDK (a
     * common situation: Gradle below 8.5 cannot run on Java 21), in which case a {@code gradle}
     * on the PATH is used instead.
     */
    String gradle() {
        Path wrapper = projectDir.resolve(File.separatorChar == '\\' ? "gradlew.bat" : "gradlew");
        boolean systemGradle = isOnPath("gradle");
        if (Files.exists(wrapper)) {
            String version = wrapperVersion();
            String required = minimumGradleForJdk(Runtime.version().feature());
            if (version != null && required != null && compareVersions(version, required) < 0) {
                if (systemGradle) {
                    gradleNote = "wrapper pins Gradle " + version + ", which cannot run on Java "
                        + Runtime.version().feature() + " (needs " + required + "+); using the gradle on the PATH instead";
                    return "gradle";
                }
                gradleNote = "wrapper pins Gradle " + version + ", which cannot run on Java "
                    + Runtime.version().feature() + " (needs " + required + "+) and no gradle is on the PATH; "
                    + "run mutant-killer with an older JDK or install Gradle";
            }
            if (!Files.isExecutable(wrapper)) {
                wrapper.toFile().setExecutable(true);
            }
            return wrapper.toString();
        }
        return "gradle";
    }

    /** Version pinned in gradle/wrapper/gradle-wrapper.properties, or null. */
    String wrapperVersion() {
        Path props = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.exists(props)) {
            return null;
        }
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("gradle-(\\d+(?:\\.\\d+)*)-(?:bin|all)\\.zip")
                .matcher(Files.readString(props, StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Lowest Gradle release that runs on the given Java feature version (toolchain compatibility table). */
    static String minimumGradleForJdk(int javaVersion) {
        if (javaVersion >= 24) {
            return "8.14";
        }
        if (javaVersion >= 23) {
            return "8.10";
        }
        if (javaVersion >= 22) {
            return "8.8";
        }
        if (javaVersion >= 21) {
            return "8.5";
        }
        if (javaVersion >= 20) {
            return "8.3";
        }
        if (javaVersion >= 19) {
            return "7.6";
        }
        if (javaVersion >= 18) {
            return "7.5";
        }
        if (javaVersion >= 17) {
            return "7.3";
        }
        if (javaVersion >= 16) {
            return "7.0";
        }
        if (javaVersion >= 15) {
            return "6.7";
        }
        return null;
    }

    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int y = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static boolean isOnPath(String exe) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isBlank() && Files.isExecutable(Path.of(dir, exe))) {
                return true;
            }
        }
        return false;
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
