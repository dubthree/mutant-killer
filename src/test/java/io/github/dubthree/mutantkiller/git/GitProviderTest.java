package io.github.dubthree.mutantkiller.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class GitProviderTest {

    // --- detect() ---

    @Nested
    class Detect {
        @Test
        void github() {
            GitProvider p = GitProvider.detect("https://github.com/owner/repo", "tok");
            assertEquals("GitHub", p.name());
        }

        @Test
        void githubSsh() {
            GitProvider p = GitProvider.detect("git@github.com:owner/repo.git", "tok");
            assertEquals("GitHub", p.name());
        }

        @Test
        void gitlabCom() {
            GitProvider p = GitProvider.detect("https://gitlab.com/owner/repo", "tok");
            assertEquals("GitLab", p.name());
        }

        @Test
        void gitlabSelfHosted() {
            GitProvider p = GitProvider.detect("https://gitlab.company.com/group/repo", "tok");
            assertEquals("GitLab", p.name());
        }

        @Test
        void azureDevOps() {
            GitProvider p = GitProvider.detect("https://dev.azure.com/org/project/_git/repo", "tok");
            assertEquals("Azure DevOps", p.name());
        }

        @Test
        void azureVisualStudio() {
            GitProvider p = GitProvider.detect("https://myorg.visualstudio.com/project/_git/repo", "tok");
            assertEquals("Azure DevOps", p.name());
        }

        @Test
        void nullUrl() {
            assertThrows(IllegalArgumentException.class, () -> GitProvider.detect(null, "tok"));
        }

        @Test
        void unknownProvider() {
            assertThrows(IllegalArgumentException.class,
                () -> GitProvider.detect("https://bitbucket.org/owner/repo", "tok"));
        }
    }

    // --- injectAuth() ---

    @Nested
    class InjectAuth {
        @Test
        void github() {
            String result = GitProvider.injectAuth("https://github.com/owner/repo.git", "mytoken");
            assertEquals("https://x-access-token:mytoken@github.com/owner/repo.git", result);
        }

        @Test
        void gitlab() {
            String result = GitProvider.injectAuth("https://gitlab.com/owner/repo.git", "mytoken");
            assertEquals("https://oauth2:mytoken@gitlab.com/owner/repo.git", result);
        }

        @Test
        void gitlabSelfHosted() {
            String result = GitProvider.injectAuth("https://gitlab.company.com/group/repo", "tok");
            assertEquals("https://oauth2:tok@gitlab.company.com/group/repo", result);
        }

        @Test
        void azureDevOps() {
            String result = GitProvider.injectAuth("https://dev.azure.com/org/project/_git/repo", "pat");
            assertEquals("https://pat@dev.azure.com/org/project/_git/repo", result);
        }

        @Test
        void azureVisualStudio() {
            String result = GitProvider.injectAuth("https://myorg.visualstudio.com/project/_git/repo", "pat");
            assertEquals("https://pat@myorg.visualstudio.com/project/_git/repo", result);
        }

        @Test
        void sshUrlPassedThrough() {
            String url = "git@github.com:owner/repo.git";
            assertEquals(url, GitProvider.injectAuth(url, "tok"));
        }

        @Test
        void unknownUrlPassedThrough() {
            String url = "https://bitbucket.org/owner/repo";
            assertEquals(url, GitProvider.injectAuth(url, "tok"));
        }
    }

    // --- RepoInfo / AzureRepoInfo records ---

    @Test
    void repoInfoRecord() {
        var info = new GitProvider.RepoInfo("owner", "repo");
        assertEquals("owner", info.owner());
        assertEquals("repo", info.repo());
    }

    @Test
    void azureRepoInfoRecord() {
        var info = new GitProvider.AzureRepoInfo("org", "proj", "repo");
        assertEquals("org", info.organization());
        assertEquals("proj", info.project());
        assertEquals("repo", info.repo());
    }
}
