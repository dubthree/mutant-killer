package io.github.dubthree.mutantkiller.pit;

/**
 * Represents a single mutation result from a PIT report.
 */
public record MutationResult(
    String mutatedClass,
    String mutatedMethod,
    String methodDescription,
    int lineNumber,
    String mutator,
    String description,
    String status,
    String sourceFile,
    String killingTest
) {
    /**
     * Returns true if this mutation survived (was not killed by tests).
     */
    public boolean survived() {
        return "SURVIVED".equals(status) || "NO_COVERAGE".equals(status);
    }

    /**
     * Returns true if this mutation was killed by a test.
     */
    public boolean killed() {
        return "KILLED".equals(status);
    }

    /**
     * Returns a human-readable description of the mutation.
     */
    public String humanReadable() {
        return String.format("%s.%s (line %d): %s", 
            mutatedClass, mutatedMethod, lineNumber, getMutatorDescription());
    }

    /**
     * Returns a human-readable description of what the mutator does.
     */
    public String getMutatorDescription() {
        return switch (mutator) {
            case "org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator" ->
                "Changed conditional boundary (e.g., < to <=)";
            case "org.pitest.mutationtest.engine.gregor.mutators.IncrementsMutator" ->
                "Changed increment/decrement (e.g., ++ to --)";
            case "org.pitest.mutationtest.engine.gregor.mutators.MathMutator" ->
                "Changed math operator (e.g., + to -)";
            case "org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator" ->
                "Negated conditional (e.g., == to !=)";
            case "org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator" ->
                "Changed return value";
            case "org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator" ->
                "Removed void method call";
            case "org.pitest.mutationtest.engine.gregor.mutators.EmptyObjectReturnValsMutator" ->
                "Replaced return with empty object";
            case "org.pitest.mutationtest.engine.gregor.mutators.FalseReturnValsMutator" ->
                "Replaced boolean return with false";
            case "org.pitest.mutationtest.engine.gregor.mutators.TrueReturnValsMutator" ->
                "Replaced boolean return with true";
            case "org.pitest.mutationtest.engine.gregor.mutators.NullReturnValsMutator" ->
                "Replaced return with null";
            case "org.pitest.mutationtest.engine.gregor.mutators.PrimitiveReturnsMutator" ->
                "Replaced primitive return with 0";
            default -> description != null ? description : mutator;
        };
    }
}
