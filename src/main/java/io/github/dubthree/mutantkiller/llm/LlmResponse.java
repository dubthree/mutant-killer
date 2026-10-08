package io.github.dubthree.mutantkiller.llm;

/**
 * A completion returned by an {@link LlmClient}, with usage accounting.
 *
 * @param text         the assistant text (all text blocks concatenated)
 * @param model        the model that actually served the request, if known
 * @param inputTokens  input tokens billed (0 if unknown)
 * @param outputTokens output tokens billed (0 if unknown)
 * @param costUsd      estimated cost in USD, or {@code Double.NaN} if unknown
 */
public record LlmResponse(String text, String model, long inputTokens, long outputTokens, double costUsd) {

    public boolean hasCost() {
        return !Double.isNaN(costUsd);
    }
}
