package io.multiagent.intent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exposes configuration knobs for connecting Intent-Agent to AI-Core.
 */
@ConfigurationProperties(prefix = "intent-agent")
public class IntentAgentProperties {

    private final AiCore aiCore = new AiCore();

    public AiCore getAiCore() {
        return aiCore;
    }

    /**
     * AI-Core connection information (base URL, endpoint, timeout).
     */
    public static class AiCore {
        /**
         * Base URL du service AI-Core (ex: http://ai-core:8081).
         */
        private String baseUrl = "http://ai-core:8081";

        /**
         * Path de l'endpoint de classification.
         */
        private String classifyPath = "/intent/classify";

        /**
         * Timeout maximum pour l'appel HTTP.
         */
        private Duration timeout = Duration.ofSeconds(8);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getClassifyPath() {
            return classifyPath;
        }

        public void setClassifyPath(String classifyPath) {
            this.classifyPath = classifyPath;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
