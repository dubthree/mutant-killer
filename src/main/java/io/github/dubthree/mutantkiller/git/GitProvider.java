package io.github.dubthree.mutantkiller.git;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hosting provider operations (pull requests, comments). Plain git operations are provider
 * agnostic and live in {@link RepositoryManager}.
 */
public interface GitProvider {

    /**
     * Create a pull/merge request.
     *
     * @return URL of the created (or already existing) PR
     */
    String createPullRequest(String headBranch, String baseBranch, String title, String body) throws Exception;

    /**
     * Add a comment to a PR/MR.
     */
    void addComment(String prId, String comment) throws Exception;

    String name();

    /**
     * User name to pair with a token for HTTP basic auth against this provider's git endpoint.
     */
    String gitUsername();

    /**
     * Detect the provider from a repository URL. {@code token} may be null for read-only use.
     */
    static GitProvider detect(String repoUrl, String token) {
        if (repoUrl == null) {
            throw new IllegalArgumentException("Repository URL cannot be null");
        }
        String lowerUrl = repoUrl.toLowerCase();

        if (lowerUrl.contains("github.com")) {
            RepoInfo info = parseGitHubUrl(repoUrl);
            return new GitHubProvider(token, info.owner(), info.repo());
        }
        if (lowerUrl.contains("dev.azure.com") || lowerUrl.contains("visualstudio.com")) {
            AzureRepoInfo info = parseAzureUrl(repoUrl);
            return new AzureDevOpsProvider(token, info.organization(), info.project(), info.repo());
        }
        if (lowerUrl.contains("gitlab")) {
            RepoInfo info = parseGitLabUrl(repoUrl);
            return new GitLabProvider(token, extractBaseUrl(repoUrl), info.owner(), info.repo());
        }
        throw new IllegalArgumentException("Could not detect git provider from URL: " + repoUrl);
    }

    /**
     * Turn an ssh style URL into https so a token can be used, leaving https URLs untouched.
     */
    static String toHttps(String repoUrl) {
        Matcher m = Pattern.compile("^(?:ssh://)?git@([^:/]+)[:/](.+)$").matcher(repoUrl);
        if (m.matches()) {
            return "https://" + m.group(1) + "/" + m.group(2);
        }
        return repoUrl;
    }

    static RepoInfo parseGitHubUrl(String url) {
        Matcher matcher = Pattern.compile("github\\.com[/:]([^/]+)/([^/]+?)(?:\\.git)?/?$").matcher(url);
        if (matcher.find()) {
            return new RepoInfo(matcher.group(1), matcher.group(2));
        }
        throw new IllegalArgumentException("Could not parse GitHub URL: " + url);
    }

    static RepoInfo parseGitLabUrl(String url) {
        Matcher matcher = Pattern.compile("(?:https?://[^/]+/|git@[^:]+:)(.+?)(?:\\.git)?/?$").matcher(url);
        if (matcher.find()) {
            String path = matcher.group(1);
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash > 0) {
                return new RepoInfo(path.substring(0, lastSlash), path.substring(lastSlash + 1));
            }
        }
        throw new IllegalArgumentException("Could not parse GitLab URL: " + url);
    }

    static AzureRepoInfo parseAzureUrl(String url) {
        Matcher matcher = Pattern.compile("dev\\.azure\\.com/([^/]+)/([^/]+)/_git/([^/]+?)(?:\\.git)?/?$").matcher(url);
        if (matcher.find()) {
            return new AzureRepoInfo(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        matcher = Pattern.compile("([^./]+)\\.visualstudio\\.com/([^/]+)/_git/([^/]+?)(?:\\.git)?/?$").matcher(url);
        if (matcher.find()) {
            return new AzureRepoInfo(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        throw new IllegalArgumentException("Could not parse Azure DevOps URL: " + url);
    }

    static String extractBaseUrl(String url) {
        Matcher matcher = Pattern.compile("(https?://[^/]+)").matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "https://gitlab.com";
    }

    record RepoInfo(String owner, String repo) {}

    record AzureRepoInfo(String organization, String project, String repo) {}
}
