package io.github.dubthree.mutantkiller.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeCliClientTest {

    @Test
    void parsesSuccessfulResultDocument() throws Exception {
        String json = """
            {"type":"result","subtype":"success","is_error":false,"result":"```java\\nvoid x() {}\\n```",
             "total_cost_usd":0.0123,"usage":{"input_tokens":10,"cache_read_input_tokens":5,"cache_creation_input_tokens":0,"output_tokens":7},
             "modelUsage":{"claude-sonnet-5-5":{}}}
            """;
        LlmResponse r = ClaudeCliClient.parse(json);
        assertEquals("```java\nvoid x() {}\n```", r.text());
        assertEquals(15, r.inputTokens());
        assertEquals(7, r.outputTokens());
        assertEquals(0.0123, r.costUsd(), 1e-9);
        assertEquals("claude-sonnet-5-5", r.model());
        assertTrue(r.hasCost());
    }

    @Test
    void errorResultsRaise() {
        String json = "{\"type\":\"result\",\"is_error\":true,\"result\":\"Authentication error\"}";
        LlmException e = assertThrows(LlmException.class, () -> ClaudeCliClient.parse(json));
        assertTrue(e.getMessage().contains("Authentication error"));
    }

    @Test
    void nonJsonRaises() {
        assertThrows(LlmException.class, () -> ClaudeCliClient.parse("not json"));
        assertThrows(LlmException.class, () -> ClaudeCliClient.parse("[1,2]"));
    }

    @Test
    void missingCostIsNaN() throws Exception {
        LlmResponse r = ClaudeCliClient.parse("{\"result\":\"hi\"}");
        assertFalse(r.hasCost());
        assertEquals(0, r.inputTokens());
    }

    @Test
    void availabilityCheckHandlesMissingExecutable() {
        assertFalse(ClaudeCliClient.isAvailable("definitely-not-a-real-binary-12345"));
        assertTrue(ClaudeCliClient.isAvailable("sh"));
    }

    @Test
    void apiCostEstimateKnowsCurrentModels() {
        assertEquals(4.0 + 20.0, AnthropicApiClient.estimateCost("claude-opus-5-5", 1_000_000, 1_000_000), 1e-9);
        assertTrue(Double.isNaN(AnthropicApiClient.estimateCost("claude-unknown", 1, 1)));
    }
}
