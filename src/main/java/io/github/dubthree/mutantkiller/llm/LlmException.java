package io.github.dubthree.mutantkiller.llm;

/**
 * Raised when a model call fails for a reason that is not the caller's fault
 * (network, authentication, rate limiting, CLI not found, ...).
 */
public class LlmException extends Exception {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
