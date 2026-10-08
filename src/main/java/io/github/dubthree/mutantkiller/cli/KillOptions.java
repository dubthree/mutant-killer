package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import picocli.CommandLine.Option;

import java.io.File;
import java.time.Duration;

/**
 * Options shared by the commands that call the model.
 */
public class KillOptions {

    @Option(names = {"--model"}, description = "Model id or alias (default: ${DEFAULT-VALUE})",
        defaultValue = MutantKillerConfig.DEFAULT_MODEL)
    String model;

    @Option(names = {"--backend"}, description = "LLM backend: auto, api (Anthropic API, needs ANTHROPIC_API_KEY) "
        + "or cli (Claude Code CLI, uses your Claude login). Default: ${DEFAULT-VALUE}", defaultValue = "auto")
    String backend;

    @Option(names = {"--claude-executable"}, description = "Path to the claude CLI (default: ${DEFAULT-VALUE})",
        defaultValue = "claude")
    String claudeExecutable;

    @Option(names = {"--max-mutants"}, description = "Maximum mutants to process (default: ${DEFAULT-VALUE})",
        defaultValue = "10")
    int maxMutants;

    @Option(names = {"--max-attempts"}, description = "Model attempts per mutant before giving up (default: ${DEFAULT-VALUE})",
        defaultValue = "3")
    int maxAttempts;

    @Option(names = {"--no-verify"}, negatable = true, description = "Skip the scoped PIT re-run that proves a mutant is dead")
    boolean noVerify;

    @Option(names = {"--include-no-coverage"}, description = "Also target NO_COVERAGE mutants (lines no test reaches)")
    boolean includeNoCoverage;

    @Option(names = {"--target-classes"}, description = "PIT targetClasses glob(s), comma separated, e.g. com.acme.util.*")
    String targetClasses;

    @Option(names = {"--target-tests"}, description = "PIT targetTests glob(s), comma separated. PIT defaults this to "
        + "--target-classes, which misses tests that live in another package (e.g. org.json.junit.*)")
    String targetTests;

    @Option(names = {"--build-system"}, description = "Force maven or gradle when both are present")
    String buildSystem;

    @Option(names = {"--build-timeout"}, description = "Minutes a single build/PIT run may take (default: ${DEFAULT-VALUE})",
        defaultValue = "60")
    int buildTimeoutMinutes;

    @Option(names = {"--prompt-dir"}, description = "Directory with custom system.md / analyze.md templates")
    File promptDir;

    @Option(names = {"--report"}, description = "Write a JSON report of every mutant and outcome to this file")
    File report;

    @Option(names = {"-v", "--verbose"}, description = "Stream build output and show stack traces")
    boolean verbose;

    MutantKillerConfig toConfig(boolean dryRun) {
        return MutantKillerConfig.builder()
            .model(model)
            .backend(backend)
            .claudeExecutable(claudeExecutable)
            .promptDir(promptDir != null ? promptDir.toPath() : null)
            .dryRun(dryRun)
            .verbose(verbose)
            .maxAttempts(maxAttempts)
            .verify(!noVerify)
            .buildTimeout(Duration.ofMinutes(buildTimeoutMinutes))
            .build();
    }
}
