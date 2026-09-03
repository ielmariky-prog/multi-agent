package io.multiagent.reassign.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEventDTO {

    private String correlationId;
    private String decision;           // CREATE_EXPENSE | ASK_USER | QUERY_EXPENSE
    private String missingField;       // optionnel
    private String questionPromptId;   // optionnel
    private Instant timestamp;
}