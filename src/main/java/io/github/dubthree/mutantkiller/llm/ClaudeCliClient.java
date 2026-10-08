package io.github.dubthree.mutantkiller.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link LlmClient} that shells out to the Claude Code CLI in headless mode
 * ({@code claude -p}). The CLI brings its own authentication, so this backend works with a
 * Claude subscription or an already logged-in cloud session and needs no API key.
 * <p>
 * Tools are disabled and session persistence is off, so each call is a plain
 * "system prompt + user prompt -> text" completion. The CLI is run from an empty scratch
 * directory so it never picks up a CLAUDE.md from the project under test.
 */
public class ClaudeCliClient implements LlmClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String executable;
    private final String model;
    private final Duration timeout;
    private final boolean verbose;
    private final Path scratchDir;

    ClaudeCliClient(String executable, String model, Duration timeout, boolean verbose) throws LlmException {
        this.executable = executable;
        this.model = model;
        this.timeout = timeout;
        this.verbose = verbose;
        try {
            this.scratchDir = Files.createTempDirectory("mutant-killer-claude");
        } catch (IOException e) {
            throw new LlmException("Could not create scratch directory for the Claude CLI", e);
        }
    }

    public static ClaudeCliClient create(MutantKillerConfig config) throws LlmException {
        String exe = config.claudeExecutable() == null ? "claude" : config.claudeExecutable();
        if (!isAvailable(exe)) {
            throw new LlmException("Claude CLI backend selected but '" + exe + "' was not found on the PATH. "
                + "Install Claude Code (https://claude.ai/code) and run `claude` once to log in.");
        }
        return new ClaudeCliClient(exe, config.model(), config.llmTimeout(), config.verbose());
    }

    /**
     * True if the executable can be located (absolute path exists or it is on the PATH).
     */
    public static boolean isAvailable(String executable) {
        String exe = executable == null ? "claude" : executable;
        if (exe.contains("/") || exe.contains("\\")) {
            return Files.isExecutable(Path.of(exe));
        }
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, exe);
            if (Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) throws LlmException {
        List<String> command = new ArrayList<>(List.of(
            executable,
            "-p",
            "--output-format", "json",
            "--tools", "",
            "--no-session-persistence",
            "--model", model
        ));
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            command.add("--system-prompt");
            command.add(systemPrompt);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(scratchDir.toFile());
        pb.redirectErrorStream(false);
        // Make sure a nested invocation never inherits this process's session identity.
        pb.environment().remove("CLAUDE_CODE_SESSION_ID");

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new LlmException("Could not start the Claude CLI (" + executable + "): " + e.getMessage(), e);
        }

        try {
            // The prompt goes on stdin so there is no argument-length limit.
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(userPrompt.getBytes(StandardCharsets.UTF_8));
            }
            // Drain stderr concurrently so a chatty CLI cannot block on a full pipe.
            StringBuilder stderr = new StringBuilder();
            Thread stderrPump = new Thread(() -> {
                try {
                    stderr.append(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // nothing useful to do
                }
            });
            stderrPump.setDaemon(true);
            stderrPump.start();

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new LlmException("Claude CLI timed out after " + timeout);
            }
            stderrPump.join(5_000);

            if (verbose && !stderr.isEmpty()) {
                System.err.println("[claude stderr] " + stderr.toString().strip());
            }
            if (process.exitValue() != 0 && stdout.isBlank()) {
                throw new LlmException("Claude CLI exited with code " + process.exitValue() + ": " + stderr.toString().strip());
            }
            return parse(stdout);
        } catch (IOException e) {
            throw new LlmException("I/O error talking to the Claude CLI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new LlmException("Interrupted while waiting for the Claude CLI", e);
        }
    }

    /**
     * Parse the CLI's {@code --output-format json} result document.
     */
    static LlmResponse parse(String stdout) throws LlmException {
        JsonNode root;
        try {
            root = JSON.readTree(stdout);
        } catch (IOException e) {
            throw new LlmException("Claude CLI returned non-JSON output: " + abbreviate(stdout), e);
        }
        if (root == null || !root.isObject()) {
            throw new LlmException("Claude CLI returned unexpected output: " + abbreviate(stdout));
        }
        String result = root.path("result").asText("");
        if (root.path("is_error").asBoolean(false)) {
            throw new LlmException("Claude CLI reported an error: " + (result.isBlank() ? abbreviate(stdout) : result));
        }
        long in = root.path("usage").path("input_tokens").asLong(0)
            + root.path("usage").path("cache_read_input_tokens").asLong(0)
            + root.path("usage").path("cache_creation_input_tokens").asLong(0);
        long out = root.path("usage").path("output_tokens").asLong(0);
        double cost = root.has("total_cost_usd") ? root.get("total_cost_usd").asDouble() : Double.NaN;
        String servedModel = null;
        JsonNode modelUsage = root.path("modelUsage");
        if (modelUsage.isObject() && modelUsage.fieldNames().hasNext()) {
            servedModel = modelUsage.fieldNames().next();
        }
        return new LlmResponse(result, servedModel, in, out, cost);
    }

    private static String abbreviate(String s) {
        String t = s == null ? "" : s.strip();
        return t.length() > 500 ? t.substring(0, 500) + "..." : t;
    }

    @Override
    public String name() {
        return "Claude Code CLI";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(scratchDir);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
