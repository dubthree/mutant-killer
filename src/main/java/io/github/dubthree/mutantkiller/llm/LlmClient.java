package io.github.dubthree.mutantkiller.llm;

import io.github.dubthree.mutantkiller.config.MutantKillerConfig;

/**
 * Minimal abstraction over "send a system prompt and a user prompt, get text back".
 * <p>
 * Two implementations ship:
 * <ul>
 *   <li>{@link AnthropicApiClient} - the Anthropic Messages API via the official Java SDK
 *       (needs {@code ANTHROPIC_API_KEY});</li>
 *   <li>{@link ClaudeCliClient} - shells out to the Claude Code CLI ({@code claude -p}),
 *       which uses whatever login the CLI already has (a Claude subscription, cloud session
 *       credentials, or an API key). This is the right choice when you want to spend
 *       Claude plan credits rather than API credits.</li>
 * </ul>
 */
public interface LlmClient extends AutoCloseable {

    /**
     * Send one prompt and return the model's reply.
     */
    LlmResponse complete(String systemPrompt, String userPrompt) throws LlmException;

    /**
     * Human-readable backend name for logs.
     */
    String name();

    /**
     * Model id this client is configured to use.
     */
    String model();

    @Override
    default void close() {
    }

    /**
     * Pick a backend from configuration.
     * <ul>
     *   <li>{@code api} - always use the SDK; requires an API key.</li>
     *   <li>{@code cli} - always use the Claude Code CLI.</li>
     *   <li>{@code auto} (default) - SDK if an API key is available, otherwise the CLI if it is on the PATH.</li>
     * </ul>
     */
    static LlmClient create(MutantKillerConfig config) throws LlmException {
        String backend = config.backend() == null ? "auto" : config.backend().toLowerCase();
        switch (backend) {
            case "api":
                return AnthropicApiClient.create(config);
            case "cli":
                return ClaudeCliClient.create(config);
            case "auto":
                if (config.apiKey() != null && !config.apiKey().isBlank()) {
                    return AnthropicApiClient.create(config);
                }
                if (ClaudeCliClient.isAvailable(config.claudeExecutable())) {
                    return ClaudeCliClient.create(config);
                }
                throw new LlmException(
                    "No LLM backend available. Either set ANTHROPIC_API_KEY (Anthropic API) "
                    + "or install and log in to the Claude Code CLI (`claude`) so mutant-killer can use "
                    + "your Claude plan. Use --backend api|cli to force one.");
            default:
                throw new LlmException("Unknown backend '" + config.backend() + "' (expected auto, api or cli)");
        }
    }
}
