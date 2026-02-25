package io.github.dubthree.mutantkiller.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MutantKillerConfigTest {

    @Test
    void builder_defaults() {
        var config = MutantKillerConfig.builder()
            .apiKey("sk-test")
            .build();

        assertEquals("claude-sonnet-4-20250514", config.model());
        assertEquals("sk-test", config.apiKey());
        assertFalse(config.dryRun());
        assertFalse(config.verbose());
        assertNull(config.sourceDir());
        assertNull(config.testDir());
        assertNull(config.promptDir());
    }

    @Test
    void builder_allFields() {
        var config = MutantKillerConfig.builder()
            .apiKey("key")
            .model("custom-model")
            .sourceDir(Path.of("/src"))
            .testDir(Path.of("/test"))
            .promptDir(Path.of("/prompts"))
            .dryRun(true)
            .verbose(true)
            .build();

        assertEquals("custom-model", config.model());
        assertEquals("key", config.apiKey());
        assertEquals(Path.of("/src"), config.sourceDir());
        assertEquals(Path.of("/test"), config.testDir());
        assertEquals(Path.of("/prompts"), config.promptDir());
        assertTrue(config.dryRun());
        assertTrue(config.verbose());
    }

    @Test
    void builder_throwsWithoutApiKey() {
        // Clear env var scenario - if ANTHROPIC_API_KEY is not set
        var builder = MutantKillerConfig.builder();
        // Only throws if both explicit key and env var are absent
        // We can't control env vars easily, so just test explicit null + blank
        var b2 = MutantKillerConfig.builder().apiKey("");
        assertThrows(IllegalStateException.class, b2::build);
    }

    @Test
    void builder_chainable() {
        // Verify fluent API returns same builder
        var builder = MutantKillerConfig.builder();
        assertSame(builder, builder.model("m"));
        assertSame(builder, builder.apiKey("k"));
        assertSame(builder, builder.sourceDir(Path.of(".")));
        assertSame(builder, builder.testDir(Path.of(".")));
        assertSame(builder, builder.promptDir(Path.of(".")));
        assertSame(builder, builder.dryRun(true));
        assertSame(builder, builder.verbose(true));
    }

    @Test
    void loadPrompt_fromCustomDir(@TempDir Path tmp) throws IOException {
        Path promptDir = tmp.resolve("prompts");
        Files.createDirectories(promptDir);
        Files.writeString(promptDir.resolve("system.md"), "custom prompt content");

        var config = new MutantKillerConfig(
            "model", "key", null, null, promptDir, false, false);

        assertEquals("custom prompt content", config.loadPrompt("system"));
    }

    @Test
    void loadPrompt_returnsNullForMissing() {
        var config = new MutantKillerConfig(
            "model", "key", null, null, null, false, false);

        // No custom dir, no classpath resource for "nonexistent" -> null
        assertNull(config.loadPrompt("nonexistent-prompt-xyz"));
    }

    @Test
    void loadPrompt_nullPromptDir() {
        var config = new MutantKillerConfig(
            "model", "key", null, null, null, false, false);
        // Should not throw, just try classpath
        config.loadPrompt("anything");
    }
}
