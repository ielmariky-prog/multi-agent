package io.multiagent.reasoning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.multiagent.core.model.ReasoningResult;
import io.multiagent.reasoning.config.ReasoningAgentProperties;
import io.multiagent.reasoning.dto.ReasoningAnalysisDTO;
import io.multiagent.reasoning.mapper.ReasoningMapper;
import java.time.Duration;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;

@Slf4j
@Service
public class ReasoningAgentService {

    private final WebClient http;
    private final ReasoningMapper mapper;
    private final ReasoningAgentProperties.AiCore aiCore;
    private final ObjectMapper objectMapper;

    public ReasoningAgentService(
            WebClient.Builder builder,
            ReasoningMapper mapper,
            ReasoningAgentProperties properties,
            ObjectMapper objectMapper) {
        this.http = builder.baseUrl(properties.getAiCore().getBaseUrl()).build();
        this.mapper = mapper;
        this.aiCore = properties.getAiCore();
        this.objectMapper = objectMapper;
    }

    public ReasoningAnalysisDTO analyze(String text) {
        String normalizedText = extractOriginalText(text);
        String correlationId = extractCorrelationId(text);

        log.info("🧠 [Reasoning-Agent] Analyse : {} (correlationId={})", normalizedText, correlationId);

        try {
            ReasoningResult rr = http
                    .post()
                    .uri(aiCore.getAnalyzePath())
                    .contentType(MediaType.TEXT_PLAIN)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(normalizedText)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> new IllegalStateException(
                                            "AI-Core error %s: %s".formatted(response.statusCode(), body))))
                    .bodyToMono(ReasoningResult.class)
                    .timeout(timeout())
                    .block();

            if (rr == null) {
                throw new IllegalStateException("AI-Core renvoie null");
            }

            ReasoningAnalysisDTO dto = mapper.toDTO(rr);
            injectCorrelationId(dto, correlationId);
            return dto;
        } catch (Exception e) {
            log.error("❌ Erreur Reasoning-Agent", e);
            return errorFallback(normalizedText);
        }
    }

    private String extractOriginalText(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        try {
            JsonNode root = objectMapper.readTree(input);
            JsonNode originalText = root.path("originalText");
            if (!originalText.isMissingNode() && !originalText.asText().isBlank()) {
                return originalText.asText();
            }
            JsonNode raw = root.path("raw");
            if (!raw.isMissingNode() && !raw.asText().isBlank()) {
                return raw.asText();
            }
        } catch (Exception ignored) {
        }
        return input;
    }

    private String extractCorrelationId(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(input);
            JsonNode cid = root.path("correlationId");
            if (!cid.isMissingNode() && !cid.asText().isBlank()) {
                return cid.asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void injectCorrelationId(ReasoningAnalysisDTO dto, String correlationId) {
        if (correlationId == null) return;
        if (dto.getMetadata() == null) {
            dto.setMetadata(new HashMap<>());
        }
        dto.getMetadata().put("correlationId", correlationId);
    }

    private ReasoningAnalysisDTO errorFallback(String text) {
        ReasoningAnalysisDTO dto = new ReasoningAnalysisDTO();
        dto.setType("error");
        dto.setStatus("ERROR");
        dto.setExplanation("Erreur Reasoning-Agent");
        dto.setOriginalText(text);
        dto.setRaw(text);
        dto.setConfidence(0.0);
        return dto;
    }

    private Duration timeout() {
        return aiCore.getTimeout() == null ? Duration.ofSeconds(8) : aiCore.getTimeout();
    }
}
