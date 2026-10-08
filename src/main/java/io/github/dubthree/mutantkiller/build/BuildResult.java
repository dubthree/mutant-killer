package io.github.dubthree.mutantkiller.build;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Outcome of one build tool invocation.
 *
 * @param exitCode process exit code (-1 when the process timed out)
 * @param output   combined stdout/stderr, truncated to the last part if very large
 * @param logFile  full log on disk, may be null
 */
public record BuildResult(int exitCode, String output, Path logFile) {

    public boolean success() {
        return exitCode == 0;
    }

    /**
     * The last {@code lines} lines of output, which is where build tools print the reason
     * for a failure.
     */
    public String tail(int lines) {
        String[] all = output.split("\n");
        int from = Math.max(0, all.length - lines);
        return Arrays.stream(all, from, all.length).collect(Collectors.joining("\n"));
    }

    /**
     * Only the lines that look like compiler or test failures, which is what a model needs
     * to fix a broken test. Falls back to the tail when nothing matches.
     */
    public String errorSummary(int maxLines) {
        var interesting = Arrays.stream(output.split("\n"))
            .filter(l -> l.contains("ERROR") || l.contains("error:") || l.contains("FAIL")
                || l.contains("Exception") || l.contains("expected") || l.contains("Tests run")
                || l.contains("cannot find symbol") || l.contains("symbol:") || l.contains("location:")
                || l.contains("incompatible types") || l.contains("Caused by") || l.contains("at ")
                && l.contains("Test."))
            .filter(l -> !l.contains("[INFO] BUILD"))
            .toList();
        if (interesting.isEmpty()) {
            return tail(maxLines);
        }
        int from = Math.max(0, interesting.size() - maxLines);
        return String.join("\n", interesting.subList(from, interesting.size()));
    }
}
