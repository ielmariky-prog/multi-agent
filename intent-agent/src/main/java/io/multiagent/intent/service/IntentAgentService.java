package io.multiagent.intent.service;

import io.multiagent.core.model.IntentResult;
import io.multiagent.core.model.IntentRequest;
import io.multiagent.intent.config.IntentAgentProperties;
import io.multiagent.intent.dto.IntentDTO;
import io.multiagent.intent.mapper.IntentMapper;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class IntentAgentService {

    private final WebClient http;
    private final IntentMapper mapper;
    private final KafkaTemplate<String, IntentDTO> kafkaTemplate;
    private final IntentAgentProperties.AiCore aiCore;
    private final String intentOutputTopic;

    public IntentAgentService(
            WebClient.Builder builder,
            IntentMapper mapper,
            KafkaTemplate<String, IntentDTO> kafkaTemplate,
            IntentAgentProperties properties,
            @Value("${spring.kafka.topics.intent-output}") String intentOutputTopic) {
        this.http = builder.baseUrl(properties.getAiCore().getBaseUrl()).build();
        this.mapper = mapper;
        this.kafkaTemplate = kafkaTemplate;
        this.aiCore = properties.getAiCore();
        this.intentOutputTopic = intentOutputTopic;
    }

    /**
     * Analyse un texte, récupère l'intent depuis AI-Core,
     * le mappe en DTO (avec correlationId) et le publie dans Kafka.
     *
     * @param text          texte brut de l'utilisateur (déjà extrait du message Kafka)
     * @param correlationId identifiant de corrélation pipeline, null si chemin direct
     */
    public IntentDTO classifyAndPublish(String text, String correlationId) {

        log.info("🧠 [Intent-Agent] Analyse du texte : {}", text);

        IntentRequest payload = new IntentRequest();
        payload.setText(text);

        // 1) Appel AI-Core
        IntentResult intentResult = http
                .post()
                .uri(aiCore.getClassifyPath())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalStateException(
                                        "AI-Core error %s: %s".formatted(response.statusCode(), body))))
                .bodyToMono(IntentResult.class)
                .timeout(timeout())
                .block();

        if (intentResult == null) {
            log.error("❌ AI-Core a renvoyé null pour la classification.");
            IntentDTO fallback = new IntentDTO();
            fallback.setIntent("error");
            fallback.setConfidence(0.0);
            fallback.setExplanation("AI-Core n'a pas répondu.");
            fallback.setOriginalText(text);
            fallback.setCorrelationId(correlationId);
            return fallback;
        }

        // 2) Mapping vers DTO local + propagation du correlationId
        IntentDTO dto = mapper.toDTO(intentResult);
        dto.setCorrelationId(correlationId);

        // 3) Publication dans Kafka (le correlationId voyage dans le JSON du DTO)
        kafkaTemplate.send(intentOutputTopic, dto);
        log.info("📤 [Intent-Agent] Intent publié dans Kafka topic={} : intent={}, correlationId={}",
                intentOutputTopic, dto.getIntent(), correlationId);

        return dto;
    }

    private Duration timeout() {
        return aiCore.getTimeout() == null ? Duration.ofSeconds(8) : aiCore.getTimeout();
    }
}
