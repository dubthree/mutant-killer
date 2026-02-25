package io.github.dubthree.mutantkiller.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for the mutant killer.
 */
public record MutantKillerConfig(
    String model,
    String apiKey,
    Path sourceDir,
    Path testDir,
    Path promptDir,
    boolean dryRun,
    boolean verbose
) {
    public static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load a prompt template, checking custom promptDir first, then defaults.
     */
    public String loadPrompt(String name) {
        // Try custom prompt directory first
        if (promptDir != null) {
            Path customPrompt = promptDir.resolve(name + ".md");
            if (Files.exists(customPrompt)) {
                try {
                    return Files.readString(customPrompt);
                } catch (IOException e) {
                    System.err.println("Warning: could not read custom prompt " + customPrompt + ": " + e.getMessage());
                }
            }
        }

        // Load from classpath
        try (var stream = getClass().getResourceAsStream("/prompts/" + name + ".md")) {
            if (stream != null) {
                return new String(stream.readAllBytes());
            }
        } catch (IOException e) {
            // Fall through to null
        }

        return null;
    }

    /**
     * Returns a string representation that excludes the API key.
     */
    @Override
    public String toString() {
        return "MutantKillerConfig[model=" + model
            + ", sourceDir=" + sourceDir
            + ", testDir=" + testDir
            + ", promptDir=" + promptDir
            + ", dryRun=" + dryRun
            + ", verbose=" + verbose + "]";
    }

    public static class Builder {
        private String model = DEFAULT_MODEL;
        private String apiKey;
        private Path sourceDir;
        private Path testDir;
        private Path promptDir;
        private boolean dryRun = false;
        private boolean verbose = false;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder sourceDir(Path sourceDir) {
            this.sourceDir = sourceDir;
            return this;
        }

        public Builder testDir(Path testDir) {
            this.testDir = testDir;
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

        public MutantKillerConfig build() {
            if (apiKey == null) {
                apiKey = System.getenv("ANTHROPIC_API_KEY");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                    "Anthropic API key not set. Set ANTHROPIC_API_KEY environment variable.");
            }
            return new MutantKillerConfig(model, apiKey, sourceDir, testDir, promptDir, dryRun, verbose);
        }
    }
}
