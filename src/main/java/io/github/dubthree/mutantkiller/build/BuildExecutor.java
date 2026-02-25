package io.github.dubthree.mutantkiller.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes build commands and mutation testing for Maven or Gradle projects.
 */
public abstract class BuildExecutor {

    protected final Path projectDir;

    protected BuildExecutor(Path projectDir) {
        this.projectDir = projectDir;
    }

    /**
     * Detect the build system and return an appropriate executor.
     */
    public static BuildExecutor detect(Path projectDir) {
        if (Files.exists(projectDir.resolve("pom.xml"))) {
            return new MavenExecutor(projectDir);
        }
        if (Files.exists(projectDir.resolve("build.gradle")) || 
            Files.exists(projectDir.resolve("build.gradle.kts"))) {
            return new GradleExecutor(projectDir);
        }
        return null;
    }

    /**
     * Get the name of the build system.
     */
    public abstract String name();

    /**
     * Run mutation testing and return the path to the mutations report.
     */
    public abstract File runMutationTesting() throws IOException, InterruptedException;

    /**
     * Get the main source directory.
     */
    public abstract Path sourceDir();

    /**
     * Get the test source directory.
     */
    public abstract Path testDir();

    /**
     * Recursively search for mutations.xml in a directory.
     */
    protected File findMutationsXml(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.getName().equals("mutations.xml")) {
                return file;
            }
            if (file.isDirectory()) {
                File found = findMutationsXml(file);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Execute a command in the project directory.
     */
    protected int execute(List<String> command, boolean verbose) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (verbose) {
                    System.out.println("  " + line);
                }
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Build command timed out");
        }

        return process.exitValue();
    }

    /**
     * Maven build executor.
     */
    static class MavenExecutor extends BuildExecutor {
        
        MavenExecutor(Path projectDir) {
            super(projectDir);
        }

        @Override
        public String name() {
            return "Maven";
        }

        @Override
        public File runMutationTesting() throws IOException, InterruptedException {
            // First, ensure PIT plugin is configured or use command line
            List<String> command = new ArrayList<>();
            
            // Use mvnw if available, otherwise mvn
            if (Files.exists(projectDir.resolve("mvnw"))) {
                command.add(projectDir.resolve("mvnw").toString());
            } else {
                command.add("mvn");
            }
            
            command.add("test-compile");
            command.add("org.pitest:pitest-maven:mutationCoverage");
            command.add("-DoutputFormats=XML,HTML");
            command.add("-DtimestampedReports=false");
            
            System.out.println("  Running: " + String.join(" ", command));
            
            int exitCode = execute(command, false);
            if (exitCode != 0) {
                throw new IOException("Maven PIT execution failed with exit code " + exitCode);
            }

            // Find the mutations.xml report
            Path pitReportsDir = projectDir.resolve("target/pit-reports");
            if (Files.exists(pitReportsDir)) {
                File mutationsXml = pitReportsDir.resolve("mutations.xml").toFile();
                if (mutationsXml.exists()) {
                    return mutationsXml;
                }
                // Try to find in subdirectories (timestamped reports)
                return findMutationsXml(pitReportsDir.toFile());
            }
            return null;
        }

        @Override
        public Path sourceDir() {
            return projectDir.resolve("src/main/java");
        }

        @Override
        public Path testDir() {
            return projectDir.resolve("src/test/java");
        }
    }

    /**
     * Gradle build executor.
     */
    static class GradleExecutor extends BuildExecutor {
        
        GradleExecutor(Path projectDir) {
            super(projectDir);
        }

        @Override
        public String name() {
            return "Gradle";
        }

        @Override
        public File runMutationTesting() throws IOException, InterruptedException {
            List<String> command = new ArrayList<>();
            
            // Use gradlew if available, otherwise gradle
            if (Files.exists(projectDir.resolve("gradlew"))) {
                command.add(projectDir.resolve("gradlew").toString());
            } else {
                command.add("gradle");
            }
            
            command.add("pitest");
            command.add("--no-daemon");
            
            System.out.println("  Running: " + String.join(" ", command));
            
            int exitCode = execute(command, false);
            if (exitCode != 0) {
                throw new IOException("Gradle PIT execution failed with exit code " + exitCode);
            }

            // Find the mutations.xml report
            Path pitReportsDir = projectDir.resolve("build/reports/pitest");
            if (Files.exists(pitReportsDir)) {
                File mutationsXml = pitReportsDir.resolve("mutations.xml").toFile();
                if (mutationsXml.exists()) {
                    return mutationsXml;
                }
                return findMutationsXml(pitReportsDir.toFile());
            }
            return null;
        }

        @Override
        public Path sourceDir() {
            return projectDir.resolve("src/main/java");
        }

        @Override
        public Path testDir() {
            return projectDir.resolve("src/test/java");
        }
    }
}
