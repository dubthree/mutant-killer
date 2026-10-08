package io.github.dubthree.mutantkiller.kill;

import io.github.dubthree.mutantkiller.codegen.TestImprover;
import io.github.dubthree.mutantkiller.pit.MutationResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Outcome of trying to kill one mutant.
 *
 * @param mutant        the mutant
 * @param status        what happened
 * @param attempts      model calls made
 * @param testFile      test file that was changed or created (null if nothing was kept)
 * @param testClassFqn  fully qualified test class name
 * @param generatedCode the code that was kept, or the last attempt when failed
 * @param message       one-line explanation
 * @param history       failed attempts with their reasons
 * @param inputTokens   total input tokens spent
 * @param outputTokens  total output tokens spent
 * @param costUsd       total estimated cost (NaN when unknown)
 * @param elapsed       wall time
 */
public record KillResult(
    MutationResult mutant,
    Status status,
    int attempts,
    Path testFile,
    String testClassFqn,
    String generatedCode,
    String message,
    List<TestImprover.Attempt> history,
    long inputTokens,
    long outputTokens,
    double costUsd,
    Duration elapsed
) {
    public enum Status {
        /** The new test passes and a scoped PIT run shows the mutant is now detected. */
        KILLED_VERIFIED,
        /** The new test compiles and passes; PIT verification was disabled. */
        TESTS_PASS_UNVERIFIED,
        /** Every attempt failed (did not compile, test failed, or the mutant still survived). */
        FAILED,
        /** The model judged the mutant equivalent (no observable behaviour change), so no test can kill it. */
        EQUIVALENT,
        /** Could not even try: source/test file not found, build broken, model unavailable. */
        ERROR
    }

    public boolean success() {
        return status == Status.KILLED_VERIFIED || status == Status.TESTS_PASS_UNVERIFIED;
    }
}
