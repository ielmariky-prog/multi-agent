package io.multiagent.core.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes audit events to Kafka after each state-changing operation.
 * Used by the MCP/REST path so the audit-agent still processes every action,
 * even when the Kafka agent chain was bypassed for synchronous processing.
 */
@Slf4j
@Component
public class AuditEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;

    @Value("${spring.kafka-topics.audit-input:audit.events.in}")
    private String auditTopic;

    public AuditEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = new ObjectMapper();
    }

    /**
     * Builds and sends an AuditEventDTO-compatible JSON message.
     * Field names match AuditEventDTO in audit-agent so it deserializes correctly.
     */
    public void publish(String action, String status, String llmModel,
                        String llmResponse, long durationMs, Map<String, Object> metadata) {
        String correlationId = UUID.randomUUID().toString();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("correlationId", correlationId);
        event.put("agentName", "ai-core-mcp");
        event.put("action", action);
        event.put("status", status);
        event.put("llmModel", llmModel);
        event.put("llmResponse", llmResponse);
        event.put("durationMs", durationMs);
        event.put("timestamp", Instant.now().toEpochMilli());
        event.put("metadata", metadata != null ? metadata : Map.of());

        try {
            String json = mapper.writeValueAsString(event);
            kafkaTemplate.send(auditTopic, correlationId, json);
            log.info("📤 Audit event published → topic={} action={} status={}", auditTopic, action, status);
        } catch (Exception e) {
            // Ne jamais bloquer le flux principal pour un problème d'audit
            log.warn("⚠️ Failed to publish audit event (action={}): {}", action, e.getMessage());
        }
    }
}
