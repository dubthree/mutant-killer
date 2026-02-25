package io.github.dubthree.mutantkiller.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubProviderTest {

    @Test
    void name() {
        var provider = new GitHubProvider("tok", "owner", "repo");
        assertEquals("GitHub", provider.name());
    }

    @Test
    void constructsWithParameters() {
        // Just verifying construction doesn't throw
        assertDoesNotThrow(() -> new GitHubProvider("token", "myowner", "myrepo"));
    }
}
