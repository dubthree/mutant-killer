package io.github.dubthree.mutantkiller.analysis;

import io.github.dubthree.mutantkiller.build.TestFramework;
import io.github.dubthree.mutantkiller.pit.MutationResult;

import java.nio.file.Path;

/**
 * Everything the model needs to know about one surviving mutation.
 *
 * @param mutation              the PIT mutation
 * @param sourceFile            file that declares the mutated (outer) class
 * @param sourceCode            full text of that file
 * @param methodSource          text of the mutated method, or null when it could not be located
 * @param contextAroundMutation a few lines around the mutated line, with a {@code >>>} marker
 * @param testFile              test file to extend (may not exist yet)
 * @param testClassFqn          fully qualified name of that test class
 * @param existingTestCode      current text of the test file, or null when it does not exist
 * @param testFramework         framework the test must use
 */
public record MutantAnalysis(
    MutationResult mutation,
    Path sourceFile,
    String sourceCode,
    String methodSource,
    String contextAroundMutation,
    Path testFile,
    String testClassFqn,
    String existingTestCode,
    TestFramework testFramework
) {
    public boolean hasExistingTest() {
        return existingTestCode != null;
    }

    public String testSimpleName() {
        return testClassFqn.substring(testClassFqn.lastIndexOf('.') + 1);
    }

    public String testPackage() {
        int dot = testClassFqn.lastIndexOf('.');
        return dot >= 0 ? testClassFqn.substring(0, dot) : "";
    }
}
