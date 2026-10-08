package io.github.dubthree.mutantkiller.pit;

import java.util.Objects;

/**
 * A single mutation from a PIT {@code mutations.xml} report.
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
    String killingTest,
    int index,
    int block
) {
    public MutationResult {
        mutatedClass = Objects.requireNonNullElse(mutatedClass, "");
        mutatedMethod = Objects.requireNonNullElse(mutatedMethod, "");
        status = Objects.requireNonNullElse(status, "UNKNOWN");
    }

    /**
     * Mutations that survived without being detected. Same semantics as the original tool:
     * {@code SURVIVED} and {@code NO_COVERAGE}.
     */
    public boolean survived() {
        return isSurvived() || isNoCoverage();
    }

    /** The mutation was executed by at least one test and no test failed. */
    public boolean isSurvived() {
        return "SURVIVED".equals(status);
    }

    /** No test executes the mutated line at all. */
    public boolean isNoCoverage() {
        return "NO_COVERAGE".equals(status);
    }

    /** A test failed because of the mutation. */
    public boolean killed() {
        return "KILLED".equals(status);
    }

    /**
     * PIT counts these as "detected": a test failed, hung or crashed because of the mutation.
     */
    public boolean isDetected() {
        return switch (status) {
            case "KILLED", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR" -> true;
            default -> false;
        };
    }

    /**
     * Fully qualified name of the top-level class that declares this mutation (inner classes
     * such as {@code Foo$Bar} map to {@code Foo}), which is where the source file lives.
     */
    public String outerClass() {
        int dollar = mutatedClass.indexOf('$');
        return dollar >= 0 ? mutatedClass.substring(0, dollar) : mutatedClass;
    }

    public String packageName() {
        int dot = outerClass().lastIndexOf('.');
        return dot >= 0 ? outerClass().substring(0, dot) : "";
    }

    public String simpleOuterClassName() {
        String outer = outerClass();
        int dot = outer.lastIndexOf('.');
        return dot >= 0 ? outer.substring(dot + 1) : outer;
    }

    /**
     * True if {@code other} is the same mutation (possibly from a different PIT run with a
     * different status).
     */
    public boolean sameMutant(MutationResult other) {
        return other != null
            && mutatedClass.equals(other.mutatedClass)
            && mutatedMethod.equals(other.mutatedMethod)
            && Objects.equals(methodDescription, other.methodDescription)
            && lineNumber == other.lineNumber
            && Objects.equals(mutator, other.mutator)
            && Objects.equals(description, other.description)
            && index == other.index;
    }

    public String humanReadable() {
        return String.format("%s.%s (line %d): %s",
            mutatedClass, mutatedMethod, lineNumber, getMutatorDescription());
    }

    /**
     * Human readable description of what the mutator does. PIT's own {@code description}
     * element is usually the most precise, so it is preferred when present.
     */
    public String getMutatorDescription() {
        if (description != null && !description.isBlank()) {
            return description;
        }
        String simple = mutator == null ? "" : mutator.substring(mutator.lastIndexOf('.') + 1);
        return switch (simple) {
            case "ConditionalsBoundaryMutator" -> "Changed conditional boundary (e.g., < to <=)";
            case "IncrementsMutator" -> "Changed increment/decrement (e.g., ++ to --)";
            case "MathMutator" -> "Changed math operator (e.g., + to -)";
            case "NegateConditionalsMutator" -> "Negated conditional (e.g., == to !=)";
            case "ReturnValsMutator" -> "Changed return value";
            case "VoidMethodCallMutator" -> "Removed void method call";
            case "EmptyObjectReturnValsMutator" -> "Replaced return with empty object";
            case "FalseReturnValsMutator" -> "Replaced boolean return with false";
            case "TrueReturnValsMutator" -> "Replaced boolean return with true";
            case "NullReturnValsMutator" -> "Replaced return with null";
            case "PrimitiveReturnsMutator" -> "Replaced primitive return with 0";
            case "InvertNegsMutator" -> "Inverted negation";
            case "RemoveConditionalMutator_EQUAL_ELSE", "RemoveConditionalMutator_EQUAL_IF",
                 "RemoveConditionalMutator_ORDER_ELSE", "RemoveConditionalMutator_ORDER_IF" ->
                "Removed conditional";
            default -> mutator == null ? "Unknown mutation" : mutator;
        };
    }
}
