package io.github.dubthree.mutantkiller.codegen;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import io.github.dubthree.mutantkiller.analysis.MutantAnalysis;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import io.github.dubthree.mutantkiller.pit.MutationResult;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses Claude to generate test improvements that kill surviving mutants.
 */
public class TestImprover {

    private static final String DEFAULT_SYSTEM_PROMPT = """
        You are an expert Java developer specializing in test-driven development and mutation testing.
        Your task is to improve unit tests to catch mutations that currently survive.
        
        When given a surviving mutation:
        1. Understand what the mutation changes (e.g., boundary conditions, return values, operators)
        2. Identify why the current tests don't catch this mutation
        3. Generate a new test method or improve an existing one to kill the mutation
        
        Guidelines:
        - Write clear, focused test methods that target the specific mutation
        - Use descriptive test method names that explain what's being tested
        - Include appropriate assertions that will fail when the mutation is applied
        - Consider edge cases and boundary conditions
        - Use JUnit 5 conventions (@Test, assertions from org.junit.jupiter.api.Assertions)
        
        Respond with ONLY the new/improved test method(s) in a Java code block.
        If improving an existing test, show the complete improved method.
        """;

    private final MutantKillerConfig config;
    private final AnthropicClient client;
    private final String systemPrompt;

    public TestImprover(MutantKillerConfig config) {
        this.config = config;
        this.client = AnthropicOkHttpClient.builder()
            .apiKey(config.apiKey())
            .build();
        
        // Load custom system prompt or use default
        String customPrompt = config.loadPrompt("system");
        this.systemPrompt = customPrompt != null ? customPrompt : DEFAULT_SYSTEM_PROMPT;
    }

    /**
     * Generate a test improvement to kill the given mutation.
     */
    public Optional<TestImprovement> improve(MutationResult mutation, MutantAnalysis analysis) {
        String prompt = buildPrompt(analysis);
        
        if (config.verbose()) {
            System.out.println("Sending prompt to Claude...");
        }

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(2048)
                .system(systemPrompt)
                .messages(List.of(
                    MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(prompt)
                        .build()
                ))
                .build();

            Message response = client.messages().create(params);
            
            String content = response.content().stream()
                .filter(block -> block instanceof TextBlock)
                .map(block -> ((TextBlock) block).text())
                .findFirst()
                .orElse("");

            String generatedCode = extractCodeBlock(content);
            
            if (generatedCode == null || generatedCode.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new TestImprovement(
                analysis,
                generatedCode,
                config.dryRun()
            ));

        } catch (Exception e) {
            System.err.println("Error calling Claude API: " + e.getMessage());
            if (config.verbose()) {
                e.printStackTrace();
            }
            return Optional.empty();
        }
    }

    private String buildPrompt(MutantAnalysis analysis) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(analysis.buildAnalysisPrompt());
        prompt.append("## Task\n");
        prompt.append("Write a test method that will FAIL when this mutation is applied, ");
        prompt.append("but PASS on the original code. This will ensure the mutation is killed.\n\n");
        prompt.append("The test should specifically target the behavior at line ");
        prompt.append(analysis.mutation().lineNumber());
        prompt.append(" that the mutation changes.\n");
        return prompt.toString();
    }

    private String extractCodeBlock(String content) {
        // Extract Java code from markdown code blocks
        Pattern pattern = Pattern.compile("```java\\s*\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // Try without language specifier
        pattern = Pattern.compile("```\\s*\\n(.*?)\\n```", Pattern.DOTALL);
        matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
