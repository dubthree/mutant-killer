package io.github.dubthree.mutantkiller.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitLabProviderTest {

    @Test
    void name() {
        var provider = new GitLabProvider("tok", "https://gitlab.com", "owner", "repo");
        assertEquals("GitLab", provider.name());
    }

    @Test
    void trailingSlashStripped() {
        // Verifies construction with trailing slash doesn't cause issues
        assertDoesNotThrow(() -> new GitLabProvider("tok", "https://gitlab.com/", "o", "r"));
    }
}
