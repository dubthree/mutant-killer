package io.github.dubthree.mutantkiller.cli;

import io.github.dubthree.mutantkiller.analysis.MutantAnalysis;
import io.github.dubthree.mutantkiller.analysis.MutantAnalyzer;
import io.github.dubthree.mutantkiller.codegen.TestImprovement;
import io.github.dubthree.mutantkiller.codegen.TestImprover;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import static io.github.dubthree.mutantkiller.config.MutantKillerConfig.DEFAULT_MODEL;
import io.github.dubthree.mutantkiller.git.GitProvider;
import io.github.dubthree.mutantkiller.git.RepositoryManager;
import io.github.dubthree.mutantkiller.build.BuildExecutor;
import io.github.dubthree.mutantkiller.pit.MutationResult;
import io.github.dubthree.mutantkiller.pit.PitReportParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Main command - clones a repo, runs mutation testing, and creates PRs for fixes.
 */
@Command(
    name = "run",
    description = "Clone repo, run mutation testing, and create PRs to kill surviving mutants"
)
public class RunCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Git repository URL (e.g., https://github.com/user/repo)")
    private String repoUrl;

    @Option(names = {"-b", "--base-branch"}, description = "Base branch to work from", defaultValue = "main")
    private String baseBranch;

    @Option(names = {"--model"}, description = "LLM model to use", defaultValue = DEFAULT_MODEL)
    private String model;

    @Option(names = {"--max-mutants"}, description = "Maximum mutants to process", defaultValue = "10")
    private int maxMutants;

    @Option(names = {"--dry-run"}, description = "Analyze and generate fixes but don't create PRs")
    private boolean dryRun;

    @Option(names = {"--work-dir"}, description = "Working directory for cloned repos")
    private File workDir;

    @Option(names = {"--prompt-dir"}, description = "Directory containing custom prompt templates")
    private File promptDir;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose;

    @Option(names = {"--token", "--github-token"}, description = "Git provider token (or set GIT_TOKEN / GITHUB_TOKEN env var)")
    private String gitToken;

    @Override
    public Integer call() throws Exception {
        // Resolve git provider token: CLI flag, then GIT_TOKEN, then GITHUB_TOKEN
        String token = gitToken;
        if (token == null || token.isBlank()) {
            token = System.getenv("GIT_TOKEN");
        }
        if (token == null || token.isBlank()) {
            token = System.getenv("GITHUB_TOKEN");
        }
        if (token == null || token.isBlank()) {
            System.err.println("Git provider token required. Set --token, GIT_TOKEN, or GITHUB_TOKEN env var.");
            return 1;
        }

        // Detect git provider from URL
        GitProvider gitProvider;
        try {
            gitProvider = GitProvider.detect(repoUrl, token);
        } catch (IllegalArgumentException e) {
            System.err.println("Could not detect git provider: " + e.getMessage());
            return 1;
        }

        System.out.println("=== Mutant Killer ===");
        System.out.println("Repository: " + repoUrl);
        System.out.println("Provider: " + gitProvider.name());
        System.out.println();

        // Set up working directory
        String repoName = extractRepoName(repoUrl);
        Path workPath = workDir != null 
            ? workDir.toPath() 
            : Path.of(System.getProperty("java.io.tmpdir"), "mutant-killer", repoName);
        
        // Initialize components
        RepositoryManager repoManager = new RepositoryManager(workPath, token);
        
        // Step 1: Clone repository
        System.out.println("Step 1: Cloning repository...");
        Path repoPath = repoManager.cloneOrUpdate(repoUrl, baseBranch);
        System.out.println("  Cloned to: " + repoPath);

        // Step 2: Detect build system and run PIT
        System.out.println("\nStep 2: Running mutation tests...");
        BuildExecutor buildExecutor = BuildExecutor.detect(repoPath);
        if (buildExecutor == null) {
            System.err.println("Could not detect build system (Maven or Gradle required)");
            return 1;
        }
        System.out.println("  Build system: " + buildExecutor.name());
        
        File mutationsReport = buildExecutor.runMutationTesting();
        if (mutationsReport == null || !mutationsReport.exists()) {
            System.err.println("Mutation testing failed or no report generated");
            return 1;
        }
        System.out.println("  Report: " + mutationsReport);

        // Step 3: Parse results
        System.out.println("\nStep 3: Analyzing results...");
        PitReportParser parser = new PitReportParser();
        List<MutationResult> mutations = parser.parse(mutationsReport);
        
        List<MutationResult> survived = mutations.stream()
            .filter(MutationResult::survived)
            .limit(maxMutants)
            .toList();

        long totalMutants = mutations.size();
        long killedMutants = mutations.stream().filter(MutationResult::killed).count();
        long survivedMutants = mutations.stream().filter(MutationResult::survived).count();
        
        System.out.println("  Total mutants: " + totalMutants);
        System.out.println("  Killed: " + killedMutants);
        System.out.println("  Survived: " + survivedMutants);
        System.out.println("  Processing: " + survived.size() + " mutants");

        if (survived.isEmpty()) {
            System.out.println("\nNo surviving mutants! Your tests are strong.");
            return 0;
        }

        // Step 4: Process each surviving mutant
        System.out.println("\nStep 4: Killing mutants...\n");
        
        MutantKillerConfig config = MutantKillerConfig.builder()
            .model(model)
            .sourceDir(buildExecutor.sourceDir())
            .testDir(buildExecutor.testDir())
            .promptDir(promptDir != null ? promptDir.toPath() : null)
            .dryRun(dryRun)
            .verbose(verbose)
            .build();

        MutantAnalyzer analyzer = new MutantAnalyzer(config);
        TestImprover improver = new TestImprover(config);

        int prsCreated = 0;
        int failures = 0;

        for (int i = 0; i < survived.size(); i++) {
            MutationResult mutant = survived.get(i);
            String mutantId = generateMutantId(mutant, i);
            
            System.out.println("--- Mutant " + (i + 1) + "/" + survived.size() + " ---");
            System.out.println("Class: " + mutant.mutatedClass());
            System.out.println("Method: " + mutant.mutatedMethod() + " (line " + mutant.lineNumber() + ")");
            System.out.println("Type: " + mutant.getMutatorDescription());

            try {
                // Analyze
                MutantAnalysis analysis = analyzer.analyze(mutant);
                
                // Generate fix
                Optional<TestImprovement> improvement = improver.improve(mutant, analysis);
                
                if (improvement.isEmpty()) {
                    System.out.println("Status: Could not generate fix");
                    failures++;
                    System.out.println();
                    continue;
                }

                if (dryRun) {
                    System.out.println("Status: Fix generated (dry run)");
                    System.out.println("\nProposed fix:");
                    System.out.println(improvement.get().diff());
                } else {
                    // Create branch
                    String branchName = "mutant-killer/fix-" + mutantId;
                    repoManager.createBranch(branchName, baseBranch);
                    
                    // Apply fix
                    improvement.get().apply();
                    
                    // Commit and push
                    String commitMsg = String.format(
                        "Kill mutant: %s.%s (line %d)%n%nMutator: %s%n%nGenerated by mutant-killer",
                        mutant.mutatedClass(),
                        mutant.mutatedMethod(),
                        mutant.lineNumber(),
                        mutant.getMutatorDescription()
                    );
                    repoManager.commitAndPush(branchName, commitMsg);
                    
                    // Create PR/MR
                    String prTitle = String.format("Kill mutant in %s.%s", 
                        simpleClassName(mutant.mutatedClass()), 
                        mutant.mutatedMethod());
                    String prBody = buildPrBody(mutant, analysis, improvement.get());
                    
                    String prUrl = gitProvider.createPullRequest(branchName, baseBranch, prTitle, prBody);
                    
                    System.out.println("Status: PR created");
                    System.out.println("PR: " + prUrl);
                    prsCreated++;
                    
                    // Return to base branch for next iteration
                    repoManager.checkout(baseBranch);
                }

            } catch (Exception e) {
                System.out.println("Status: Error - " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
                failures++;
                
                // Try to recover to base branch
                try {
                    repoManager.checkout(baseBranch);
                } catch (Exception ignored) {}
            }
            
            System.out.println();
        }

        // Summary
        System.out.println("=== Summary ===");
        System.out.println("Mutants processed: " + survived.size());
        if (dryRun) {
            System.out.println("Fixes generated: " + (survived.size() - failures));
            System.out.println("(dry run - no PRs created)");
        } else {
            System.out.println("PRs created: " + prsCreated);
        }
        System.out.println("Failures: " + failures);

        return 0;
    }

    private String generateMutantId(MutationResult mutant, int index) {
        String className = simpleClassName(mutant.mutatedClass());
        return String.format("%s-%s-%d-%d", 
            className.toLowerCase(),
            mutant.mutatedMethod().toLowerCase(),
            mutant.lineNumber(),
            index);
    }

    private String simpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    private String extractRepoName(String url) {
        return RepositoryManager.extractRepoName(url);
    }

    private String buildPrBody(MutationResult mutant, MutantAnalysis analysis, TestImprovement improvement) {
        StringBuilder body = new StringBuilder();
        body.append("## Mutation Details\n\n");
        body.append("| Property | Value |\n");
        body.append("|----------|-------|\n");
        body.append("| Class | `").append(mutant.mutatedClass()).append("` |\n");
        body.append("| Method | `").append(mutant.mutatedMethod()).append("` |\n");
        body.append("| Line | ").append(mutant.lineNumber()).append(" |\n");
        body.append("| Mutator | ").append(mutant.getMutatorDescription()).append(" |\n\n");
        
        body.append("## Why This Mutation Survived\n\n");
        body.append("The existing tests did not verify the specific behavior ");
        body.append("that this mutation changes. ");
        body.append("This PR adds a test that will fail if the mutation is applied.\n\n");
        
        body.append("## Changes\n\n");
        body.append("```java\n");
        body.append(improvement.generatedCode());
        body.append("\n```\n\n");
        
        body.append("---\n");
        body.append("*Generated by [mutant-killer](https://github.com/dubthree/mutant-killer)*");
        
        return body.toString();
    }
}
