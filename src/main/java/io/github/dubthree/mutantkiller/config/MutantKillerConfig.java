package io.github.dubthree.mutantkiller.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Runtime configuration shared by the commands.
 *
 * @param model            model id (or CLI alias such as {@code sonnet}) to use
 * @param backend          {@code auto}, {@code api} or {@code cli}
 * @param apiKey           Anthropic API key, may be null when using the CLI backend
 * @param claudeExecutable path or name of the Claude Code CLI binary
 * @param promptDir        optional directory with custom {@code system.md} / {@code analyze.md}
 * @param dryRun           generate and verify locally but never commit, push or open PRs
 * @param verbose          stream build output and stack traces
 * @param maxAttempts      how many times to ask the model again after a failed attempt
 * @param verify           re-run PIT on the mutated class to prove the mutant is dead
 * @param buildTimeout     maximum wall time for a single build/PIT invocation
 * @param llmTimeout       maximum wall time for a single model call
 * @param maxOutputTokens  max output tokens for API calls
 */
public record MutantKillerConfig(
    String model,
    String backend,
    String apiKey,
    String claudeExecutable,
    Path promptDir,
    boolean dryRun,
    boolean verbose,
    int maxAttempts,
    boolean verify,
    Duration buildTimeout,
    Duration llmTimeout,
    long maxOutputTokens
) {
    public static final String DEFAULT_MODEL = "claude-opus-5-5";

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load a prompt template, checking the custom prompt directory first, then the bundled defaults.
     *
     * @return the template text, or null if neither location has it
     */
    public String loadPrompt(String name) {
        if (promptDir != null) {
            Path custom = promptDir.resolve(name + ".md");
            if (Files.exists(custom)) {
                try {
                    return Files.readString(custom);
                } catch (IOException e) {
                    System.err.println("Warning: could not read " + custom + ", using bundled prompt (" + e.getMessage() + ")");
                }
            }
        }
        try (var stream = getClass().getResourceAsStream("/prompts/" + name + ".md")) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // fall through
        }
        return null;
    }

    public static class Builder {
        private String model = DEFAULT_MODEL;
        private String backend = "auto";
        private String apiKey = System.getenv("ANTHROPIC_API_KEY");
        private String claudeExecutable = "claude";
        private Path promptDir;
        private boolean dryRun = false;
        private boolean verbose = false;
        private int maxAttempts = 3;
        private boolean verify = true;
        private Duration buildTimeout = Duration.ofMinutes(60);
        private Duration llmTimeout = Duration.ofMinutes(15);
        private long maxOutputTokens = 8192;

        public Builder model(String model) {
            if (model != null && !model.isBlank()) {
                this.model = model;
            }
            return this;
        }

        public Builder backend(String backend) {
            if (backend != null && !backend.isBlank()) {
                this.backend = backend;
            }
            return this;
        }

        public Builder apiKey(String apiKey) {
            if (apiKey != null && !apiKey.isBlank()) {
                this.apiKey = apiKey;
            }
            return this;
        }

        public Builder claudeExecutable(String claudeExecutable) {
            if (claudeExecutable != null && !claudeExecutable.isBlank()) {
                this.claudeExecutable = claudeExecutable;
            }
            return this;
        }

        public Builder promptDir(Path promptDir) {
            this.promptDir = promptDir;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
            return this;
        }

        public Builder verify(boolean verify) {
            this.verify = verify;
            return this;
        }

        public Builder buildTimeout(Duration buildTimeout) {
            if (buildTimeout != null) {
                this.buildTimeout = buildTimeout;
            }
            return this;
        }

        public Builder llmTimeout(Duration llmTimeout) {
            if (llmTimeout != null) {
                this.llmTimeout = llmTimeout;
            }
            return this;
        }

        public Builder maxOutputTokens(long maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public MutantKillerConfig build() {
            return new MutantKillerConfig(model, backend, apiKey, claudeExecutable, promptDir, dryRun, verbose,
                maxAttempts, verify, buildTimeout, llmTimeout, maxOutputTokens);
        }
    }
}
