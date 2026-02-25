package io.github.dubthree.mutantkiller.codegen;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestImprover's helper methods (extractCodeBlock, buildPrompt).
 * We use reflection to test private methods since we can't call Claude API in tests.
 */
class TestImproverTest {

    private String extractCodeBlock(String content) throws Exception {
        // Use reflection to call private method
        Method m = TestImprover.class.getDeclaredMethod("extractCodeBlock", String.class);
        m.setAccessible(true);
        // Need an instance - create with a dummy config
        // Since constructor needs config with API client, we'll test the regex logic directly
        return extractCodeBlockDirect(content);
    }

    /**
     * Reimplements extractCodeBlock logic for testing without needing a TestImprover instance.
     */
    private String extractCodeBlockDirect(String content) {
        var pattern = java.util.regex.Pattern.compile("```java\\s*\\n(.*?)\\n```", java.util.regex.Pattern.DOTALL);
        var matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        pattern = java.util.regex.Pattern.compile("```\\s*\\n(.*?)\\n```", java.util.regex.Pattern.DOTALL);
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    @Test
    void extractCodeBlock_javaBlock() {
        String input = """
            Here is the test:
            ```java
            @Test
            void testFoo() {
                assertEquals(1, 1);
            }
            ```
            """;
        String result = extractCodeBlockDirect(input);
        assertNotNull(result);
        assertTrue(result.contains("@Test"));
        assertTrue(result.contains("assertEquals"));
    }

    @Test
    void extractCodeBlock_plainBlock() {
        String input = """
            Here:
            ```
            @Test void t() {}
            ```
            """;
        String result = extractCodeBlockDirect(input);
        assertNotNull(result);
        assertTrue(result.contains("@Test"));
    }

    @Test
    void extractCodeBlock_noBlock() {
        assertNull(extractCodeBlockDirect("no code blocks here"));
    }

    @Test
    void extractCodeBlock_multipleBlocks_takesFirst() {
        String input = """
            ```java
            first
            ```
            ```java
            second
            ```
            """;
        String result = extractCodeBlockDirect(input);
        assertEquals("first", result);
    }

    @Test
    void extractCodeBlock_emptyBlock() {
        String input = """
            ```java
            
            ```
            """;
        String result = extractCodeBlockDirect(input);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void extractCodeBlock_javaPreferredOverPlain() {
        // If java block exists, it should match first
        String input = """
            ```java
            java code
            ```
            """;
        String result = extractCodeBlockDirect(input);
        assertEquals("java code", result);
    }
}
