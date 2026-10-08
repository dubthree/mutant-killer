package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.analysis.MutantAnalyzer;
import io.github.dubthree.mutantkiller.build.BuildExecutor;
import io.github.dubthree.mutantkiller.codegen.TestImprover;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import io.github.dubthree.mutantkiller.git.GitCredentials;
import io.github.dubthree.mutantkiller.git.GitProvider;
import io.github.dubthree.mutantkiller.git.RepositoryManager;
import io.github.dubthree.mutantkiller.kill.KillLoop;
import io.github.dubthree.mutantkiller.kill.KillResult;
import io.github.dubthree.mutantkiller.llm.LlmClient;
import io.github.dubthree.mutantkiller.llm.LlmException;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import io.github.dubthree.mutantkiller.pit.PitReportParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Clone a repository, run PIT, and open one pull request per mutant that could be killed.
 */
@Command(
    name = "run",
    description = "Clone a repo, run mutation testing, and create PRs that kill surviving mutants"
)
public class RunCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Repository URL (e.g. https://github.com/user/repo)")
    private String repoUrl;

    @Option(names = {"-b", "--base-branch"}, description = "Base branch (default: the remote's default branch)")
    private String baseBranch;

    @Option(names = {"--dry-run"}, description = "Generate and verify fixes in the local clone but do not commit, push or open PRs. "
        + "No git token is needed for public repositories.")
    private boolean dryRun;

    @Option(names = {"--work-dir"}, description = "Where to clone (default: a temp directory)")
    private File workDir;

    @Option(names = {"--token", "--github-token"}, description = "Git hosting token (or GITHUB_TOKEN / GITLAB_TOKEN / AZURE_DEVOPS_TOKEN / GIT_TOKEN env var)")
    private String token;

    @Option(names = {"--branch-prefix"}, description = "Prefix for fix branches (default: ${DEFAULT-VALUE})", defaultValue = "mutant-killer/fix-")
    private String branchPrefix;

    @Mixin
    private KillOptions options;

    @Override
    public Integer call() throws Exception {
        String resolvedToken = resolveToken();
        if ((resolvedToken == null || resolvedToken.isBlank()) && !dryRun) {
            System.err.println("A git hosting token is required to push branches and open PRs. "
                + "Pass --token, set GITHUB_TOKEN (or GITLAB_TOKEN / AZURE_DEVOPS_TOKEN), or use --dry-run.");
            return 1;
        }

        GitProvider gitProvider = null;
        try {
            gitProvider = GitProvider.detect(repoUrl, resolvedToken);
        } catch (IllegalArgumentException e) {
            if (!dryRun) {
                System.err.println("Could not detect git provider: " + e.getMessage());
                return 1;
            }
        }

        MutantKillerConfig config = options.toConfig(dryRun);
        // Fail fast on a missing model backend before spending minutes on PIT.
        LlmClient llm;
        try {
            llm = LlmClient.create(config);
        } catch (LlmException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        System.out.println("=== Mutant Killer ===");
        System.out.println("Repository: " + repoUrl);
        System.out.println("Provider:   " + (gitProvider != null ? gitProvider.name() : "unknown (dry run)"));
        System.out.println("Model:      " + llm.model() + " via " + llm.name());
        System.out.println("Mode:       " + (dryRun ? "dry run (local only)" : "pull requests"));
        System.out.println();

        String repoName = RepositoryManager.extractRepoName(repoUrl);
        Path workPath = workDir != null
            ? workDir.toPath().toAbsolutePath()
            : Path.of(System.getProperty("java.io.tmpdir"), "mutant-killer", repoName);
        Path logDir = workPath.resolve("logs");

        GitCredentials credentials = null;
        if (resolvedToken != null && !resolvedToken.isBlank()) {
            credentials = new GitCredentials(gitProvider != null ? gitProvider.gitUsername() : "x-access-token", resolvedToken);
        }
        RepositoryManager repoManager = new RepositoryManager(workPath, credentials);

        System.out.println("Step 1: Cloning repository...");
        Path repoPath = repoManager.cloneOrUpdate(repoUrl, baseBranch);
        if (baseBranch == null) {
            baseBranch = repoManager.currentBranch();
        }
        System.out.println("  Cloned to:   " + repoPath);
        System.out.println("  Base branch: " + baseBranch);

        BuildExecutor build = BuildExecutor.detect(repoPath, options.buildSystem, config.buildTimeout(), config.verbose(), logDir);
        if (build == null) {
            System.err.println("Could not detect build system (Maven or Gradle required)");
            return 1;
        }

        RunReport report = new RunReport(repoUrl, llm.name(), llm.model());
        try (llm) {
            System.out.println("\nStep 2: Running mutation tests (" + build.name() + ")...");
            String note = build.prepare();
            if (note != null) {
                System.out.println("  " + note);
                report.note("build", note);
            }
            List<File> reports = build.runMutationTesting(options.targetClasses, options.targetTests);
            System.out.println("  Reports: " + reports.size() + " (logs in " + logDir + ")");

            System.out.println("\nStep 3: Analyzing results...");
            List<MutationResult> mutations = new PitReportParser().parseAll(reports);
            report.pitStats(mutations);
            List<MutationResult> selected = MutantSelector.select(mutations, options.includeNoCoverage, options.maxMutants);
            System.out.println("  Total mutants: " + mutations.size());
            System.out.println("  Detected:      " + mutations.stream().filter(MutationResult::isDetected).count());
            System.out.println("  Survived:      " + MutantSelector.count(mutations, "SURVIVED"));
            System.out.println("  No coverage:   " + MutantSelector.count(mutations, "NO_COVERAGE"));
            System.out.println("  Processing:    " + selected.size());
            if (selected.isEmpty()) {
                System.out.println("\nNo surviving mutants! Your tests are strong.");
                return 0;
            }

            System.out.println("\nStep 4: Killing mutants...");
            MutantAnalyzer analyzer = new MutantAnalyzer(build.sourceRoots(), build.testRoots(), build.detectTestFramework());
            TestImprover improver = new TestImprover(config, llm);
            KillLoop loop = new KillLoop(config, build, analyzer, improver, line -> System.out.println("  " + line));

            for (int i = 0; i < selected.size(); i++) {
                MutationResult mutant = selected.get(i);
                System.out.println();
                System.out.printf("--- Mutant %d/%d ---%n", i + 1, selected.size());
                System.out.println("Class:    " + mutant.mutatedClass());
                System.out.println("Method:   " + mutant.mutatedMethod() + " (line " + mutant.lineNumber() + ")");
                System.out.println("Mutation: " + mutant.getMutatorDescription());

                String branchName = branchPrefix + branchId(mutant, i);
                String prUrl = null;
                try {
                    if (!dryRun) {
                        repoManager.createBranch(branchName, baseBranch);
                    }
                    KillResult result = loop.kill(mutant);
                    System.out.println("  => " + result.status() + ": " + result.message());

                    if (result.success() && !dryRun) {
                        String commitMsg = String.format(
                            "Kill mutant: %s.%s (line %d)%n%nMutation: %s%nVerified: %s%n%nGenerated by mutant-killer",
                            mutant.mutatedClass(), mutant.mutatedMethod(), mutant.lineNumber(),
                            mutant.getMutatorDescription(),
                            result.status() == KillResult.Status.KILLED_VERIFIED ? "yes, PIT re-run shows the mutant killed" : "tests pass; PIT re-run skipped");
                        repoManager.commitAndPush(branchName, commitMsg, List.of(result.testFile()));
                        String prTitle = String.format("Kill mutant in %s.%s", mutant.simpleOuterClassName(), mutant.mutatedMethod());
                        prUrl = gitProvider.createPullRequest(branchName, baseBranch, prTitle, buildPrBody(mutant, result));
                        System.out.println("  PR: " + prUrl);
                    }
                    report.add(result, prUrl);
                } catch (Exception e) {
                    System.out.println("  => ERROR: " + e.getMessage());
                    if (config.verbose()) {
                        e.printStackTrace();
                    }
                    report.add(new KillResult(mutant, KillResult.Status.ERROR, 0, null, null, null,
                        e.getMessage(), List.of(), 0, 0, 0, java.time.Duration.ZERO), null);
                } finally {
                    if (!dryRun) {
                        try {
                            repoManager.discardChanges();
                            repoManager.checkout(baseBranch);
                        } catch (Exception ignored) {
                            // best effort; next createBranch forces a checkout anyway
                        }
                    }
                }
            }
        } finally {
            build.cleanup();
        }

        report.printSummary(dryRun);
        if (dryRun) {
            System.out.println("Generated tests are in the clone at " + repoPath + " (see `git diff`).");
        }
        if (options.report != null) {
            report.write(options.report);
            System.out.println("Report written to " + options.report);
        }
        return 0;
    }

    private String resolveToken() {
        if (token != null && !token.isBlank()) {
            return token;
        }
        for (String var : List.of("GITHUB_TOKEN", "GITLAB_TOKEN", "AZURE_DEVOPS_TOKEN", "GIT_TOKEN")) {
            String value = System.getenv(var);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static String branchId(MutationResult mutant, int index) {
        String method = mutant.mutatedMethod().replaceAll("[^A-Za-z0-9]", "");
        return String.format("%s-%s-%d-%d",
            mutant.simpleOuterClassName().toLowerCase(),
            method.toLowerCase(),
            mutant.lineNumber(),
            index);
    }

    private String buildPrBody(MutationResult mutant, KillResult result) {
        StringBuilder body = new StringBuilder();
        body.append("## Mutation details\n\n");
        body.append("| Property | Value |\n|----------|-------|\n");
        body.append("| Class | `").append(mutant.mutatedClass()).append("` |\n");
        body.append("| Method | `").append(mutant.mutatedMethod()).append("` |\n");
        body.append("| Line | ").append(mutant.lineNumber()).append(" |\n");
        body.append("| Mutation | ").append(mutant.getMutatorDescription()).append(" |\n");
        body.append("| Mutator | `").append(mutant.mutator()).append("` |\n\n");

        body.append("## Why this mutation survived\n\n");
        body.append("No existing test asserts on the behaviour this mutation changes, so PIT reported it as `")
            .append(mutant.status()).append("`. This PR adds a test whose assertion depends on exactly that behaviour.\n\n");

        body.append("## Verification\n\n");
        if (result.status() == KillResult.Status.KILLED_VERIFIED) {
            body.append("- The new test compiles and passes on the current code.\n");
            body.append("- PIT was re-run on `").append(mutant.outerClass()).append("` with `")
                .append(result.testClassFqn()).append("` and now reports this mutant as killed.\n");
        } else {
            body.append("- The new test compiles and passes on the current code (PIT re-run was skipped).\n");
        }
        if (result.attempts() > 1) {
            body.append("- Took ").append(result.attempts()).append(" attempts; earlier drafts were rejected by the build or by PIT.\n");
        }
        body.append("\n## Changes\n\n```java\n").append(result.generatedCode()).append("\n```\n\n");
        body.append("---\n*Generated by [mutant-killer](https://github.com/dubthree/mutant-killer)*");
        return body.toString();
    }
}
