package io.github.dubthree.mutantkiller.codegen;

import io.github.dubthree.mutantkiller.analysis.MutantAnalysis;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import io.github.dubthree.mutantkiller.llm.LlmClient;
import io.github.dubthree.mutantkiller.llm.LlmException;
import io.github.dubthree.mutantkiller.llm.LlmResponse;
import io.github.dubthree.mutantkiller.prompt.PromptTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the prompt for a mutation (optionally with feedback from earlier failed attempts) and
 * asks the model for a test.
 */
public class TestImprover {

    /** Source larger than this is sent as method + context only, to keep prompts bounded. */
    static final int MAX_SOURCE_CHARS = 60_000;
    static final int MAX_TEST_CHARS = 60_000;

    /**
     * One earlier try at killing the mutant.
     *
     * @param number  1-based attempt number
     * @param code    what the model produced
     * @param problem why it did not work (compiler output, failing assertion, mutant survived...)
     */
    public record Attempt(int number, String code, String problem) {
    }

    private final LlmClient llm;
    private final String systemPrompt;
    private final String analyzeTemplate;

    public TestImprover(MutantKillerConfig config, LlmClient llm) {
        this.llm = llm;
        String system = config.loadPrompt("system");
        this.systemPrompt = system != null ? system : "You are an expert Java developer. Reply with a Java code block only.";
        String analyze = config.loadPrompt("analyze");
        if (analyze == null) {
            throw new IllegalStateException("Bundled prompt template prompts/analyze.md is missing");
        }
        this.analyzeTemplate = analyze;
    }

    public LlmResponse generate(MutantAnalysis analysis, List<Attempt> history) throws LlmException {
        return llm.complete(systemPrompt, buildPrompt(analysis, history));
    }

    public String buildPrompt(MutantAnalysis analysis, List<Attempt> history) {
        Map<String, String> vars = new HashMap<>();
        var m = analysis.mutation();
        vars.put("mutatedClass", m.mutatedClass());
        vars.put("mutatedMethod", m.mutatedMethod());
        vars.put("lineNumber", String.valueOf(m.lineNumber()));
        vars.put("mutator", m.mutator() == null ? "" : m.mutator());
        vars.put("mutatorDescription", m.getMutatorDescription());
        vars.put("contextAroundMutation", analysis.contextAroundMutation());
        vars.put("methodSource", analysis.methodSource() == null ? "" : analysis.methodSource());
        vars.put("sourceFile", analysis.sourceFile() == null ? "" : analysis.sourceFile().getFileName().toString());
        vars.put("sourceCode", cap(analysis.sourceCode(), MAX_SOURCE_CHARS));
        vars.put("testClassName", analysis.testClassFqn());
        vars.put("testFramework", analysis.testFramework().description());
        vars.put("existingTestCode", analysis.hasExistingTest() ? cap(analysis.existingTestCode(), MAX_TEST_CHARS) : "");
        vars.put("feedback", renderHistory(history));
        return PromptTemplate.render(analyzeTemplate, vars);
    }

    static String renderHistory(List<Attempt> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Attempt a : history) {
            sb.append("### Attempt ").append(a.number()).append("\n\n");
            sb.append("```java\n").append(a.code() == null ? "" : a.code().strip()).append("\n```\n\n");
            sb.append("Result:\n\n```\n").append(a.problem() == null ? "" : a.problem().strip()).append("\n```\n\n");
        }
        return sb.toString();
    }

    private static String cap(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n// ... (truncated " + (text.length() - max) + " characters)\n";
    }
}
