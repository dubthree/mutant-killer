package io.github.dubthree.mutantkiller.config;

import java.nio.file.Path;

/**
 * Configuration for the mutant killer.
 */
public record MutantKillerConfig(
    String model,
    String apiKey,
    Path sourceDir,
    Path testDir,
    boolean dryRun,
    boolean verbose
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model = "claude-sonnet-4-20250514";
        private String apiKey;
        private Path sourceDir;
        private Path testDir;
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
            return new MutantKillerConfig(model, apiKey, sourceDir, testDir, dryRun, verbose);
        }
    }
}
