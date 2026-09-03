package io.multiagent.core.exception;

/**
 * Wraps low-level errors coming from the LLM provider so upstream services can react explicitly.
 */
public class LLMClientException extends RuntimeException {

    public LLMClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public LLMClientException(String message) {
        super(message);
    }
}
