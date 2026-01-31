package io.github.dubthree.mutantkiller.analysis;

import io.github.dubthree.mutantkiller.pit.MutationResult;

import java.nio.file.Path;

/**
 * Contains all the context needed to understand and fix a surviving mutation.
 */
public record MutantAnalysis(
    MutationResult mutation,
    Path sourceFile,
    String sourceCode,
    String mutatedMethod,
    String contextAroundMutation,
    Path testFile,
    String existingTestCode
) {
    /**
     * Returns true if we have an existing test file to improve.
     */
    public boolean hasExistingTest() {
        return testFile != null && existingTestCode != null;
    }

    /**
     * Builds a prompt for the LLM to understand the mutation.
     */
    public String buildAnalysisPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A mutation testing tool (PIT) found a surviving mutation in this Java code.\n\n");
        prompt.append("## Mutation Details\n");
        prompt.append("- Class: ").append(mutation.mutatedClass()).append("\n");
        prompt.append("- Method: ").append(mutation.mutatedMethod()).append("\n");
        prompt.append("- Line: ").append(mutation.lineNumber()).append("\n");
        prompt.append("- Mutation type: ").append(mutation.getMutatorDescription()).append("\n");
        prompt.append("\n## Code Context (>>> marks the mutated line)\n```java\n");
        prompt.append(contextAroundMutation);
        prompt.append("```\n\n");
        
        if (mutatedMethod != null) {
            prompt.append("## Full Method\n```java\n");
            prompt.append(mutatedMethod);
            prompt.append("\n```\n\n");
        }

        if (hasExistingTest()) {
            prompt.append("## Existing Test Class\n```java\n");
            prompt.append(existingTestCode);
            prompt.append("\n```\n\n");
        }

        return prompt.toString();
    }
}
