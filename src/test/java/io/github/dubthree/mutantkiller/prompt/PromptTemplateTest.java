package io.github.dubthree.mutantkiller.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptTemplateTest {

    @Test
    void substitutesVariablesAndDropsUnknownOnes() {
        assertEquals("Hello World ", PromptTemplate.render("Hello {{name}} {{missing}}", Map.of("name", "World")));
    }

    @Test
    void keepsIfBlocksOnlyWhenValuePresent() {
        String t = "A{{#if x}}[{{x}}]{{/if}}B{{#if y}}[y]{{/if}}C";
        assertEquals("A[1]BC", PromptTemplate.render(t, Map.of("x", "1", "y", "  ")));
        assertEquals("AB[y]C", PromptTemplate.render(t, Map.of("y", "yes")));
    }

    @Test
    void dollarSignsInValuesAreLiteral() {
        assertEquals("cost $1", PromptTemplate.render("cost {{v}}", Map.of("v", "$1")));
    }

    @Test
    void nullTemplateRendersEmpty() {
        assertEquals("", PromptTemplate.render(null, Map.of()));
    }
}
