package io.multiagent.intent.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.multiagent.intent.dto.IntentDTO;
import io.multiagent.intent.service.IntentAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentKafkaListener {

    private final IntentAgentService intentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${spring.kafka.topics.intent-input}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(String message) {
        log.info("📥 [Intent-Agent] Message reçu de Kafka : {}", message);

        String text = message;
        String correlationId = null;

        // Si le message est un JSON {text, correlationId} (pipeline path), on extrait les deux champs.
        // Sinon on traite le message entier comme du texte brut (chemin direct, backward compat).
        try {
            JsonNode root = objectMapper.readTree(message);
            if (root.has("text")) {
                text = root.get("text").asText();
                JsonNode cid = root.get("correlationId");
                if (cid != null && !cid.isNull()) {
                    correlationId = cid.asText();
                }
            }
        } catch (Exception ignored) {
            // plain text — use as-is
        }

        IntentDTO dto = intentService.classifyAndPublish(text, correlationId);

        log.info("✅ [Intent-Agent] Classification effectuée : intent={}, confidence={}, correlationId={}",
                dto.getIntent(), dto.getConfidence(), dto.getCorrelationId());
    }
}
