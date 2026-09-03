package io.multiagent.reassign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds configuration values used by Reassign-Agent (AI-Core URL, timeouts, endpoints).
 */
@ConfigurationProperties(prefix = "reassign-agent")
public class ReassignAgentProperties {

    private final AiCore aiCore = new AiCore();

    public AiCore getAiCore() {
        return aiCore;
    }

    /**
     * Settings that describe how to reach the AI-Core workflow endpoint.
     */
    public static class AiCore {
        /**
         * Base URL exposée par AI-Core.
         */
        private String baseUrl = "http://ai-core:8081";

        /**
         * Endpoint HTTP pour décider d'une action de workflow.
         */
        private String decidePath = "/workflow/reassign";

        /**
         * Timeout maximal pour l'appel.
         */
        private java.time.Duration timeout = java.time.Duration.ofSeconds(8);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getDecidePath() {
            return decidePath;
        }

        public void setDecidePath(String decidePath) {
            this.decidePath = decidePath;
        }

        public java.time.Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(java.time.Duration timeout) {
            this.timeout = timeout;
        }
    }
}
