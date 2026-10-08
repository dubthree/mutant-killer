package io.github.dubthree.mutantkiller.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain git operations: clone, branch, commit, push. Authentication is passed per command as an
 * HTTP header, so the token never lands in {@code .git/config}.
 */
public class RepositoryManager {

    private final Path workDir;
    private final GitCredentials credentials; // may be null for public read-only access
    private Path repoPath;
    private String remoteUrl;

    public RepositoryManager(Path workDir, GitCredentials credentials) {
        this.workDir = workDir;
        this.credentials = credentials;
    }

    /**
     * Clone the repository, or fetch and hard-reset it when it is already there.
     *
     * @param branch branch to check out, or null for the remote's default branch
     * @return the clone's path
     */
    public Path cloneOrUpdate(String repoUrl, String branch) throws IOException, InterruptedException {
        Files.createDirectories(workDir);
        remoteUrl = GitProvider.toHttps(repoUrl);
        repoPath = workDir.resolve(extractRepoName(repoUrl));

        if (Files.exists(repoPath.resolve(".git"))) {
            git(withAuth("fetch", "origin", "--prune"));
            if (branch == null) {
                branch = defaultBranch();
            }
            git("checkout", "-f", branch);
            git("reset", "--hard", "origin/" + branch);
            git("clean", "-fd");
        } else {
            List<String> args = new ArrayList<>(List.of("clone"));
            if (branch != null) {
                args.add("--branch");
                args.add(branch);
            }
            args.add(remoteUrl);
            args.add(repoPath.getFileName().toString());
            gitInDir(workDir, withAuth(args.toArray(new String[0])));
        }
        return repoPath;
    }

    /**
     * Name of the checked out branch.
     */
    public String currentBranch() throws IOException, InterruptedException {
        return git("rev-parse", "--abbrev-ref", "HEAD").strip();
    }

    /**
     * The remote's default branch (what {@code origin/HEAD} points at), falling back to the
     * current branch.
     */
    public String defaultBranch() throws IOException, InterruptedException {
        try {
            String ref = git("symbolic-ref", "--short", "refs/remotes/origin/HEAD").strip();
            return ref.startsWith("origin/") ? ref.substring("origin/".length()) : ref;
        } catch (IOException e) {
            try {
                String out = git("remote", "show", "origin");
                Matcher m = Pattern.compile("HEAD branch:\\s*(\\S+)").matcher(out);
                if (m.find()) {
                    return m.group(1);
                }
            } catch (IOException ignored) {
                // fall through
            }
            return currentBranch();
        }
    }

    public void createBranch(String branchName, String baseBranch) throws IOException, InterruptedException {
        git("checkout", "-f", baseBranch);
        git("checkout", "-B", branchName);
    }

    public void checkout(String branch) throws IOException, InterruptedException {
        git("checkout", "-f", branch);
    }

    /**
     * Discard uncommitted changes to tracked files and remove untracked files, except the
     * given paths which are kept as they are.
     */
    public void discardChanges() throws IOException, InterruptedException {
        git("checkout", "--", ".");
        git("clean", "-fd");
    }

    /**
     * Commit only the given files and push the branch.
     */
    public void commitAndPush(String branch, String message, List<Path> files) throws IOException, InterruptedException {
        List<String> add = new ArrayList<>(List.of("add", "--"));
        for (Path f : files) {
            add.add(repoPath.relativize(f.toAbsolutePath()).toString());
        }
        git(add.toArray(new String[0]));
        List<String> commit = new ArrayList<>();
        if (gitQuiet("config", "--get", "user.email").isBlank()) {
            commit.addAll(List.of("-c", "user.name=mutant-killer", "-c", "user.email=mutant-killer@users.noreply.github.com"));
        }
        commit.addAll(List.of("commit", "-m", message));
        git(commit.toArray(new String[0]));
        git(withAuth("push", "-u", "origin", branch, "--force"));
    }

    public Path getRepoPath() {
        return repoPath;
    }

    private String[] withAuth(String... args) {
        if (credentials == null || credentials.token() == null || credentials.token().isBlank()) {
            return args;
        }
        List<String> all = new ArrayList<>();
        all.add("-c");
        all.add("http." + hostPrefix(remoteUrl) + ".extraheader=" + credentials.basicAuthHeader());
        all.addAll(List.of(args));
        return all.toArray(new String[0]);
    }

    static String hostPrefix(String url) {
        Matcher m = Pattern.compile("^(https?://[^/]+/)").matcher(url);
        return m.find() ? m.group(1) : url;
    }

    private String git(String... args) throws IOException, InterruptedException {
        return gitInDir(repoPath, args);
    }

    private String gitQuiet(String... args) throws InterruptedException {
        try {
            return gitInDir(repoPath, args);
        } catch (IOException e) {
            return "";
        }
    }

    private String gitInDir(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("Git command timed out: git " + mask(String.join(" ", args)));
        }
        if (process.exitValue() != 0) {
            throw new IOException("git " + mask(String.join(" ", args)) + " failed:\n" + mask(output.toString()));
        }
        return output.toString();
    }

    /**
     * Hide the credential header and token in anything that reaches logs.
     */
    String mask(String text) {
        if (credentials == null || credentials.token() == null || credentials.token().isBlank()) {
            return text;
        }
        return text.replace(credentials.basicAuthHeader(), "AUTHORIZATION: basic ***")
            .replace(credentials.token(), "***");
    }

    public static String extractRepoName(String url) {
        String name = url.replaceAll("/+$", "").replaceAll("\\.git$", "");
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(':'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name.isBlank() ? "repo" : name;
    }
}
