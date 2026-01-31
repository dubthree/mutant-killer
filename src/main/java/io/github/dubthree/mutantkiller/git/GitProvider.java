package io.github.dubthree.mutantkiller.git;

/**
 * Interface for git hosting provider operations (PRs, etc.).
 * Git operations (clone, push, branch) are provider-agnostic and handled by RepositoryManager.
 */
public interface GitProvider {

    /**
     * Create a pull/merge request.
     *
     * @param headBranch Source branch with changes
     * @param baseBranch Target branch to merge into
     * @param title PR title
     * @param body PR description (markdown)
     * @return URL of the created PR
     */
    String createPullRequest(String headBranch, String baseBranch, String title, String body) 
            throws Exception;

    /**
     * Add a comment to a PR/MR.
     *
     * @param prId PR identifier (number for GitHub, IID for GitLab, etc.)
     * @param comment Comment text
     */
    void addComment(String prId, String comment) throws Exception;

    /**
     * Get the name of this provider.
     */
    String name();

    /**
     * Detect the appropriate provider from a repository URL.
     */
    static GitProvider detect(String repoUrl, String token) {
        if (repoUrl == null) {
            throw new IllegalArgumentException("Repository URL cannot be null");
        }

        String lowerUrl = repoUrl.toLowerCase();

        // GitHub
        if (lowerUrl.contains("github.com")) {
            RepoInfo info = parseGitHubUrl(repoUrl);
            return new GitHubProvider(token, info.owner(), info.repo());
        }

        // GitLab (gitlab.com or self-hosted)
        if (lowerUrl.contains("gitlab.com") || lowerUrl.contains("gitlab")) {
            RepoInfo info = parseGitLabUrl(repoUrl);
            String baseUrl = extractBaseUrl(repoUrl);
            return new GitLabProvider(token, baseUrl, info.owner(), info.repo());
        }

        // Azure DevOps
        if (lowerUrl.contains("dev.azure.com") || lowerUrl.contains("visualstudio.com")) {
            AzureRepoInfo info = parseAzureUrl(repoUrl);
            return new AzureDevOpsProvider(token, info.organization(), info.project(), info.repo());
        }

        throw new IllegalArgumentException("Could not detect git provider from URL: " + repoUrl);
    }

    /**
     * Inject authentication token into repository URL for cloning.
     */
    static String injectAuth(String repoUrl, String token) {
        String lowerUrl = repoUrl.toLowerCase();

        // GitHub: https://x-access-token:TOKEN@github.com/...
        if (lowerUrl.contains("github.com") && repoUrl.startsWith("https://")) {
            return repoUrl.replace("https://github.com/", 
                "https://x-access-token:" + token + "@github.com/");
        }

        // GitLab: https://oauth2:TOKEN@gitlab.com/...
        if ((lowerUrl.contains("gitlab.com") || lowerUrl.contains("gitlab")) 
                && repoUrl.startsWith("https://")) {
            return repoUrl.replaceFirst("https://([^/]+)/", 
                "https://oauth2:" + token + "@$1/");
        }

        // Azure DevOps: https://TOKEN@dev.azure.com/...
        if (lowerUrl.contains("dev.azure.com") && repoUrl.startsWith("https://")) {
            return repoUrl.replace("https://dev.azure.com/", 
                "https://" + token + "@dev.azure.com/");
        }
        if (lowerUrl.contains("visualstudio.com") && repoUrl.startsWith("https://")) {
            return repoUrl.replaceFirst("https://([^.]+\\.visualstudio\\.com)/", 
                "https://" + token + "@$1/");
        }

        // Fallback - return as-is
        return repoUrl;
    }

    // URL parsing helpers

    private static RepoInfo parseGitHubUrl(String url) {
        // https://github.com/owner/repo or git@github.com:owner/repo.git
        var matcher = java.util.regex.Pattern
            .compile("github\\.com[/:]([^/]+)/([^/\\.]+)")
            .matcher(url);
        if (matcher.find()) {
            return new RepoInfo(matcher.group(1), matcher.group(2));
        }
        throw new IllegalArgumentException("Could not parse GitHub URL: " + url);
    }

    private static RepoInfo parseGitLabUrl(String url) {
        // https://gitlab.com/owner/repo or https://gitlab.company.com/group/subgroup/repo
        var matcher = java.util.regex.Pattern
            .compile("https?://[^/]+/(.+?)(?:\\.git)?$")
            .matcher(url);
        if (matcher.find()) {
            String path = matcher.group(1);
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash > 0) {
                return new RepoInfo(path.substring(0, lastSlash), path.substring(lastSlash + 1));
            }
        }
        throw new IllegalArgumentException("Could not parse GitLab URL: " + url);
    }

    private static AzureRepoInfo parseAzureUrl(String url) {
        // https://dev.azure.com/org/project/_git/repo
        // https://org.visualstudio.com/project/_git/repo
        var matcher = java.util.regex.Pattern
            .compile("dev\\.azure\\.com/([^/]+)/([^/]+)/_git/([^/\\.]+)")
            .matcher(url);
        if (matcher.find()) {
            return new AzureRepoInfo(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        
        matcher = java.util.regex.Pattern
            .compile("([^.]+)\\.visualstudio\\.com/([^/]+)/_git/([^/\\.]+)")
            .matcher(url);
        if (matcher.find()) {
            return new AzureRepoInfo(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        
        throw new IllegalArgumentException("Could not parse Azure DevOps URL: " + url);
    }

    private static String extractBaseUrl(String url) {
        var matcher = java.util.regex.Pattern
            .compile("(https?://[^/]+)")
            .matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "https://gitlab.com";
    }

    record RepoInfo(String owner, String repo) {}
    record AzureRepoInfo(String organization, String project, String repo) {}
}
