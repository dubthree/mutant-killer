package io.github.dubthree.mutantkiller.prompt;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny mustache-like renderer for the prompt templates.
 * <p>
 * Supports {@code {{name}}} substitution and {@code {{#if name}}...{{/if}}} blocks, which are
 * kept when the variable is present and non-blank and dropped otherwise. Unknown variables render
 * as empty strings so a custom template never leaks raw placeholders into a prompt.
 */
public final class PromptTemplate {

    private static final Pattern IF_BLOCK =
        Pattern.compile("\\{\\{#if\\s+([A-Za-z0-9_]+)\\s*}}(.*?)\\{\\{/if}}", Pattern.DOTALL);
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private PromptTemplate() {
    }

    public static String render(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String out = renderConditionals(template, variables);
        Matcher m = VARIABLE.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = variables.getOrDefault(m.group(1), "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String renderConditionals(String template, Map<String, String> variables) {
        Matcher m = IF_BLOCK.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = variables.get(m.group(1));
            String replacement = (value != null && !value.isBlank()) ? m.group(2) : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
