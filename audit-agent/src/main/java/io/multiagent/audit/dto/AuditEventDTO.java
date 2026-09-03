package io.multiagent.audit.dto;

import java.time.Instant;
import java.util.Map;

import lombok.Data;

@Data
public class AuditEventDTO {

    private String correlationId;
    private String agentName;
    private String action;
    private String status;

    // Métadonnées IA (produites par ai-core)
    private String llmPromptId;
    private String llmModel;
    private String llmResponse;

    // Observabilité
    private Long durationMs;
    private Instant timestamp;

    // Extensions futures (safe)
    private Map<String, Object> metadata;

}