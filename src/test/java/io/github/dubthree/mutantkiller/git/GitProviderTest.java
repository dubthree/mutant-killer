package io.github.dubthree.mutantkiller.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitProviderTest {

    @Test
    void detectsGitHubWithAndWithoutGitSuffix() {
        assertEquals("GitHub", GitProvider.detect("https://github.com/acme/widgets.git", "t").name());
        assertEquals("GitHub", GitProvider.detect("https://github.com/acme/widgets", null).name());
        assertEquals("GitHub", GitProvider.detect("git@github.com:acme/widgets.git", null).name());
        GitProvider.RepoInfo info = GitProvider.parseGitHubUrl("https://github.com/acme/widgets.git");
        assertEquals("acme", info.owner());
        assertEquals("widgets", info.repo());
        assertEquals("x-access-token", GitProvider.detect("https://github.com/acme/widgets", null).gitUsername());
    }

    @Test
    void detectsGitLabIncludingSubgroupsAndSelfHosted() {
        GitProvider.RepoInfo info = GitProvider.parseGitLabUrl("https://gitlab.example.com/group/sub/repo.git");
        assertEquals("group/sub", info.owner());
        assertEquals("repo", info.repo());
        assertEquals("GitLab", GitProvider.detect("https://gitlab.example.com/group/sub/repo", "t").name());
        assertEquals("https://gitlab.example.com", GitProvider.extractBaseUrl("https://gitlab.example.com/group/repo"));
        assertEquals("oauth2", GitProvider.detect("https://gitlab.com/g/r", "t").gitUsername());
    }

    @Test
    void detectsAzureDevOps() {
        GitProvider.AzureRepoInfo info = GitProvider.parseAzureUrl("https://dev.azure.com/org/proj/_git/repo");
        assertEquals("org", info.organization());
        assertEquals("proj", info.project());
        assertEquals("repo", info.repo());
        assertEquals("Azure DevOps", GitProvider.detect("https://dev.azure.com/org/proj/_git/repo", "t").name());
        assertEquals("", GitProvider.detect("https://dev.azure.com/org/proj/_git/repo", "t").gitUsername());
        GitProvider.AzureRepoInfo legacy = GitProvider.parseAzureUrl("https://org.visualstudio.com/proj/_git/repo");
        assertEquals("org", legacy.organization());
    }

    @Test
    void rejectsUnknownHosts() {
        assertThrows(IllegalArgumentException.class, () -> GitProvider.detect("https://bitbucket.org/a/b", "t"));
        assertThrows(IllegalArgumentException.class, () -> GitProvider.detect(null, "t"));
    }

    @Test
    void sshUrlsBecomeHttps() {
        assertEquals("https://github.com/acme/widgets.git", GitProvider.toHttps("git@github.com:acme/widgets.git"));
        assertEquals("https://github.com/acme/widgets", GitProvider.toHttps("https://github.com/acme/widgets"));
    }

    @Test
    void repoNameAndCredentialHeader() {
        assertEquals("widgets", RepositoryManager.extractRepoName("https://github.com/acme/widgets.git"));
        assertEquals("widgets", RepositoryManager.extractRepoName("git@github.com:acme/widgets"));
        assertEquals("https://github.com/", RepositoryManager.hostPrefix("https://github.com/acme/widgets"));
        GitCredentials c = new GitCredentials("x-access-token", "tok");
        assertEquals("AUTHORIZATION: basic eC1hY2Nlc3MtdG9rZW46dG9r", c.basicAuthHeader());
        RepositoryManager rm = new RepositoryManager(java.nio.file.Path.of("."), c);
        assertEquals("push with *** and AUTHORIZATION: basic ***", rm.mask("push with tok and " + c.basicAuthHeader()));
    }
}
