package io.github.dubthree.mutantkiller.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages git operations: clone, branch, commit, push.
 */
public class RepositoryManager {

    private final Path workDir;
    private final String token;
    private Path repoPath;

    public RepositoryManager(Path workDir, String token) {
        this.workDir = workDir;
        this.token = token;
    }

    /**
     * Clone a repository or update it if it already exists.
     */
    public Path cloneOrUpdate(String repoUrl, String branch) throws IOException, InterruptedException {
        // Create work directory
        Files.createDirectories(workDir);
        
        // Extract repo name from URL
        String repoName = extractRepoName(repoUrl);
        repoPath = workDir.resolve(repoName);

        if (Files.exists(repoPath.resolve(".git"))) {
            // Repository exists, fetch and reset
            git("fetch", "origin");
            git("checkout", branch);
            git("reset", "--hard", "origin/" + branch);
            git("clean", "-fd");
        } else {
            // Clone fresh - use provider-agnostic auth injection
            String authUrl = GitProvider.injectAuth(repoUrl, token);
            gitInDir(workDir, "clone", "--branch", branch, authUrl, repoName);
        }

        return repoPath;
    }

    /**
     * Create a new branch from the specified base.
     */
    public void createBranch(String branchName, String baseBranch) throws IOException, InterruptedException {
        git("checkout", baseBranch);
        git("checkout", "-B", branchName);
    }

    /**
     * Checkout a branch.
     */
    public void checkout(String branch) throws IOException, InterruptedException {
        git("checkout", branch);
    }

    /**
     * Commit all changes and push to remote.
     */
    public void commitAndPush(String branch, String message) throws IOException, InterruptedException {
        git("add", "-A");
        git("commit", "-m", message);
        git("push", "-u", "origin", branch, "--force");
    }

    /**
     * Get the current repository path.
     */
    public Path getRepoPath() {
        return repoPath;
    }

    private void git(String... args) throws IOException, InterruptedException {
        gitInDir(repoPath, args);
    }

    private void gitInDir(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        
        // Set up credential helper for token auth
        pb.environment().put("GIT_ASKPASS", "echo");
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Git command timed out: " + String.join(" ", args));
        }

        if (process.exitValue() != 0) {
            throw new IOException("Git command failed: " + String.join(" ", args) + "\n" + output);
        }
    }

    private String extractRepoName(String url) {
        // Extract repo name from URL
        String name = url.replaceAll("\\.git$", "");
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name;
    }

}
