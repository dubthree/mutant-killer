package io.github.dubthree.mutantkiller.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link LlmClient} backed by the Anthropic Messages API (official Java SDK).
 */
public class AnthropicApiClient implements LlmClient {

    /** USD per million tokens (input, output) for cost estimates. Unknown models report NaN. */
    private static final Map<String, double[]> PRICES = Map.ofEntries(
        Map.entry("claude-fable-5-1", new double[]{10.0, 50.0}),
        Map.entry("claude-fable-5", new double[]{10.0, 50.0}),
        Map.entry("claude-opus-5-5", new double[]{4.0, 20.0}),
        Map.entry("claude-opus-5", new double[]{5.0, 25.0}),
        Map.entry("claude-opus-4-8", new double[]{5.0, 25.0}),
        Map.entry("claude-opus-4-7", new double[]{5.0, 25.0}),
        Map.entry("claude-opus-4-6", new double[]{5.0, 25.0}),
        Map.entry("claude-sonnet-5-5", new double[]{2.0, 10.0}),
        Map.entry("claude-sonnet-5", new double[]{2.0, 10.0}),
        Map.entry("claude-sonnet-4-6", new double[]{3.0, 15.0}),
        Map.entry("claude-haiku-5-5", new double[]{0.10, 0.50}),
        Map.entry("claude-haiku-4-5", new double[]{1.0, 5.0})
    );

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    AnthropicApiClient(AnthropicClient client, String model, long maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public static AnthropicApiClient create(MutantKillerConfig config) throws LlmException {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new LlmException("Anthropic API backend selected but ANTHROPIC_API_KEY is not set.");
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(config.apiKey())
            .build();
        return new AnthropicApiClient(client, config.model(), config.maxOutputTokens());
    }

    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) throws LlmException {
        MessageCreateParams params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .system(systemPrompt)
            .addUserMessage(userPrompt)
            .build();

        Message response;
        try {
            response = client.messages().create(params);
        } catch (AnthropicException e) {
            throw new LlmException("Anthropic API call failed: " + e.getMessage(), e);
        }

        String text = response.content().stream()
            .flatMap(block -> block.text().stream())
            .map(t -> t.text())
            .collect(Collectors.joining("\n"));

        long in = response.usage().inputTokens();
        long out = response.usage().outputTokens();
        return new LlmResponse(text, response.model().toString(), in, out, estimateCost(model, in, out));
    }

    static double estimateCost(String model, long inputTokens, long outputTokens) {
        double[] price = PRICES.get(model);
        if (price == null) {
            return Double.NaN;
        }
        return inputTokens / 1_000_000.0 * price[0] + outputTokens / 1_000_000.0 * price[1];
    }

    @Override
    public String name() {
        return "Anthropic API";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public void close() {
        client.close();
    }
}
