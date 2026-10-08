package io.github.dubthree.mutantkiller.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs the project's build tool: mutation testing, targeted test runs, and source layout discovery.
 * <p>
 * Implementations exist for Maven ({@link MavenExecutor}) and Gradle ({@link GradleExecutor}).
 * Both can inject PIT into a project that does not configure it; see {@link #prepare()} and
 * {@link #cleanup()}, which must be paired (use try/finally).
 */
public abstract class BuildExecutor {

    /** Directories never searched for source roots or reports. */
    protected static final Set<String> SKIP_DIRS = Set.of("target", "build", ".git", ".gradle", "node_modules", "out", ".idea");

    protected final Path projectDir;
    protected final Duration timeout;
    protected final boolean verbose;
    protected final Path logDir;
    private int logCounter = 0;
    private List<Path> sourceRoots;
    private List<Path> testRoots;

    protected BuildExecutor(Path projectDir, Duration timeout, boolean verbose, Path logDir) {
        this.projectDir = projectDir;
        this.timeout = timeout;
        this.verbose = verbose;
        this.logDir = logDir;
    }

    /**
     * Detect the build system.
     *
     * @param forced "maven" or "gradle" to override detection, or null
     * @return an executor or null if neither a pom.xml nor a build.gradle(.kts) exists
     */
    public static BuildExecutor detect(Path projectDir, String forced, Duration timeout, boolean verbose, Path logDir) {
        boolean hasPom = Files.exists(projectDir.resolve("pom.xml"));
        boolean hasGradle = Files.exists(projectDir.resolve("build.gradle"))
            || Files.exists(projectDir.resolve("build.gradle.kts"));
        if (forced != null) {
            return switch (forced.toLowerCase()) {
                case "maven" -> hasPom ? new MavenExecutor(projectDir, timeout, verbose, logDir) : null;
                case "gradle" -> hasGradle ? new GradleExecutor(projectDir, timeout, verbose, logDir) : null;
                default -> throw new IllegalArgumentException("Unknown build system: " + forced);
            };
        }
        if (hasPom) {
            return new MavenExecutor(projectDir, timeout, verbose, logDir);
        }
        if (hasGradle) {
            return new GradleExecutor(projectDir, timeout, verbose, logDir);
        }
        return null;
    }

    public Path projectDir() {
        return projectDir;
    }

    public abstract String name();

    /**
     * Make the project ready for mutation testing, e.g. by injecting the PIT plugin into the
     * build. Must be followed by {@link #cleanup()}.
     *
     * @return a human readable note about what was changed, or null if nothing was
     */
    public abstract String prepare() throws IOException;

    /**
     * Undo whatever {@link #prepare()} did to the project files.
     */
    public abstract void cleanup();

    /**
     * Run PIT.
     *
     * @param targetClasses PIT target class glob(s), comma separated, or null for the project default
     * @param targetTests   PIT target test glob(s), comma separated, or null for the project default
     * @return all mutations.xml reports produced (one per module)
     */
    public abstract List<File> runMutationTesting(String targetClasses, String targetTests)
        throws BuildException, InterruptedException;

    /**
     * Compile and run a single test class.
     */
    public abstract BuildResult runTests(String testClassFqn) throws BuildException, InterruptedException;

    /**
     * Framework the project's tests use, guessed from the build descriptors.
     */
    public abstract TestFramework detectTestFramework();

    /**
     * All {@code src/main/java} directories in the project, root module first.
     */
    public List<Path> sourceRoots() {
        if (sourceRoots == null) {
            sourceRoots = findRoots("main");
        }
        return sourceRoots;
    }

    /**
     * All {@code src/test/java} directories in the project, root module first.
     */
    public List<Path> testRoots() {
        if (testRoots == null) {
            testRoots = findRoots("test");
        }
        return testRoots;
    }

    private List<Path> findRoots(String sourceSet) {
        List<Path> roots = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectDir, 8)) {
            walk.filter(Files::isDirectory)
                .filter(p -> !isSkipped(projectDir.relativize(p)))
                .filter(p -> p.getNameCount() >= 3
                    && p.getName(p.getNameCount() - 1).toString().equals("java")
                    && p.getName(p.getNameCount() - 2).toString().equals(sourceSet)
                    && p.getName(p.getNameCount() - 3).toString().equals("src"))
                .sorted(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString))
                .forEach(roots::add);
        } catch (IOException e) {
            // fall through to the conventional default below
        }
        if (roots.isEmpty()) {
            roots.add(projectDir.resolve("src").resolve(sourceSet).resolve("java"));
        }
        return roots;
    }

    protected static boolean isSkipped(Path relative) {
        for (Path part : relative) {
            if (SKIP_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find every {@code mutations.xml} below {@code dir}.
     */
    protected List<File> findReports(Path dir) {
        List<File> found = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return found;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.getFileName().toString().equals("mutations.xml"))
                .filter(p -> !p.toString().contains(File.separator + ".git" + File.separator))
                .sorted()
                .forEach(p -> found.add(p.toFile()));
        } catch (IOException e) {
            // return what we have
        }
        return found;
    }

    /**
     * Run a command in the project directory, streaming output to a log file (and to the
     * console when verbose).
     */
    protected BuildResult execute(List<String> command, String logName) throws BuildException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectDir.toFile());
        pb.redirectErrorStream(true);
        // Never let a build hang waiting for a terminal.
        pb.environment().put("CI", "true");
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");

        Path logFile = null;
        PrintWriter log = null;
        if (logDir != null) {
            try {
                Files.createDirectories(logDir);
                logFile = logDir.resolve(String.format("%03d-%s.log", ++logCounter, logName));
                log = new PrintWriter(Files.newBufferedWriter(logFile, StandardCharsets.UTF_8));
                log.println("$ " + String.join(" ", command));
            } catch (IOException e) {
                logFile = null;
            }
        }

        if (verbose) {
            System.out.println("  $ " + String.join(" ", command));
        }

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            if (log != null) {
                log.close();
            }
            throw new BuildException("Could not start '" + command.get(0) + "': " + e.getMessage(), e);
        }

        // Pump output on a separate thread so a silent long-running process still hits the timeout.
        final int keep = 400_000;
        StringBuilder output = new StringBuilder();
        final PrintWriter logWriter = log;
        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (logWriter != null) {
                        logWriter.println(line);
                    }
                    if (verbose) {
                        System.out.println("  | " + line);
                    }
                    synchronized (output) {
                        output.append(line).append('\n');
                        if (output.length() > keep * 2) {
                            output.delete(0, output.length() - keep);
                        }
                    }
                }
            } catch (IOException e) {
                // process ended; whatever was read is kept
            }
        }, "build-output");
        pump.setDaemon(true);
        pump.start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } finally {
            if (!process.isAlive()) {
                pump.join(10_000);
            }
        }
        if (!finished) {
            process.destroyForcibly();
            pump.join(5_000);
            if (log != null) {
                log.println("*** mutant-killer: timed out after " + timeout + " ***");
                log.close();
            }
            String captured;
            synchronized (output) {
                captured = output.toString();
            }
            throw new BuildException("Build command timed out after " + timeout + ": " + String.join(" ", command),
                new BuildResult(-1, captured, logFile));
        }
        pump.join(10_000);
        if (log != null) {
            log.close();
        }
        String captured;
        synchronized (output) {
            captured = output.toString();
        }
        return new BuildResult(process.exitValue(), captured, logFile);
    }

    /**
     * Read every build descriptor in the project into one string, for framework detection.
     */
    protected String readBuildFiles(String... names) {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(projectDir, 6)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> !isSkipped(projectDir.relativize(p)))
                .filter(p -> Set.of(names).contains(p.getFileName().toString()))
                .forEach(p -> {
                    try {
                        sb.append(Files.readString(p)).append('\n');
                    } catch (IOException ignored) {
                        // skip unreadable descriptor
                    }
                });
        } catch (IOException ignored) {
            // nothing readable
        }
        return sb.toString();
    }

    protected static int threads() {
        return Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    }

    /** Flags that keep unrelated quality gates from failing a scratch build. */
    protected static List<String> mavenSkipFlags() {
        return List.of(
            "-Dcheckstyle.skip=true", "-Dspotbugs.skip=true", "-Dpmd.skip=true", "-Dcpd.skip=true",
            "-Denforcer.skip=true", "-Dmaven.javadoc.skip=true", "-Dlicense.skip=true",
            "-Dspotless.check.skip=true", "-Dforbiddenapis.skip=true", "-Djacoco.skip=true",
            "-Drat.skip=true", "-Danimal.sniffer.skip=true", "-Dmaven.gitcommitid.skip=true",
            "-Dgpg.skip=true", "-Dmaven.source.skip=true", "-Dformatter.skip=true",
            "-Dmodernizer.skip=true", "-Derrorprone.skip=true", "-Djapicmp.skip=true", "-Dcyclonedx.skip=true"
        );
    }
}
