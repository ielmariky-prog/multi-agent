package io.multiagent.core.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Soumet une demande dans la chaîne d'agents via Kafka et attend le résultat final.
 * Chaque demande reçoit un correlationId unique qui voyage à travers tous les agents
 * jusqu'au workflow-output-topic, permettant de retrouver quelle réponse correspond
 * à quelle demande.
 */
@Slf4j
@Service
public class PipelineOrchestrationService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> pending
            = new ConcurrentHashMap<>();

    @Value("${spring.kafka-topics.intent-input:intent-input-topic}")
    private String intentInputTopic;

    public PipelineOrchestrationService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = new ObjectMapper();
    }

    /**
     * Publie le texte dans intent-input-topic et attend le résultat final.
     * Bloque jusqu'à timeoutMs millisecondes puis retourne un statut TIMEOUT.
     */
    public Map<String, Object> submitAndWait(String text, long timeoutMs) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pending.put(correlationId, future);

        try {
            String message = mapper.writeValueAsString(
                    Map.of("text", text, "correlationId", correlationId)
            );
            kafkaTemplate.send(intentInputTopic, correlationId, message);
            log.info("📤 Pipeline soumis → correlationId={}", correlationId);

            return future.get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            log.warn("⏳ Pipeline timeout après {}ms — correlationId={}", timeoutMs, correlationId);
            return Map.of("status", "TIMEOUT", "correlationId", correlationId,
                    "message", "Les agents n'ont pas répondu dans le délai imparti.");
        } catch (Exception e) {
            log.error("❌ Pipeline erreur — correlationId={}", correlationId, e);
            return Map.of("status", "ERROR", "correlationId", correlationId,
                    "message", e.getMessage());
        } finally {
            pending.remove(correlationId);
        }
    }

    /**
     * Appelé par WorkflowResultListener quand le résultat final arrive dans Kafka.
     * Débloque le submitAndWait correspondant.
     */
    public void resolve(String correlationId, Map<String, Object> result) {
        CompletableFuture<Map<String, Object>> future = pending.get(correlationId);
        if (future != null) {
            future.complete(result);
            log.info("✅ Pipeline résolu — correlationId={}", correlationId);
        } else {
            log.warn("⚠️ correlationId inconnu ou expiré : {}", correlationId);
        }
    }
}
