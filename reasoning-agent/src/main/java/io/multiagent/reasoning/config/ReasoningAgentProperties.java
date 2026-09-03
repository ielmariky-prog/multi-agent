package io.multiagent.reasoning.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration binding for Reasoning-Agent connectivity with AI-Core.
 */
@ConfigurationProperties(prefix = "reasoning-agent")
public class ReasoningAgentProperties {

    private final AiCore aiCore = new AiCore();

    public AiCore getAiCore() {
        return aiCore;
    }

    /**
     * Fields describing the AI-Core reasoning endpoint.
     */
    public static class AiCore {
        /**
         * Base URL du service AI-Core (par défaut http://ai-core:8081).
         */
        private String baseUrl = "http://ai-core:8081";

        /**
         * Chemin REST pour l'analyse reasoning.
         */
        private String analyzePath = "/reasoning/analyze";

        private Duration timeout = Duration.ofSeconds(8);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAnalyzePath() {
            return analyzePath;
        }

        public void setAnalyzePath(String analyzePath) {
            this.analyzePath = analyzePath;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
