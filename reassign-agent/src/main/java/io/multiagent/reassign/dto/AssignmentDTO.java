package io.multiagent.reassign.dto;

import lombok.Data;

@Data
public class AssignmentDTO {

    private String assigneeType;   // FINANCE / MANAGER / NONE...
    private String assigneeId;     // optionnel
    private String status;         // ASSIGNED / SKIPPED / ERROR
    private String reason;         // texte explicatif
    private Double confidence;     // 0.0 - 1.0

    private String originalText;   // texte original pour traçabilité
}