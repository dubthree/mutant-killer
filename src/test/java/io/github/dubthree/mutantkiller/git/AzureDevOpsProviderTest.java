package io.github.dubthree.mutantkiller.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AzureDevOpsProviderTest {

    @Test
    void name() {
        var provider = new AzureDevOpsProvider("tok", "org", "proj", "repo");
        assertEquals("Azure DevOps", provider.name());
    }

    @Test
    void constructsWithParameters() {
        assertDoesNotThrow(() -> new AzureDevOpsProvider("t", "o", "p", "r"));
    }
}
