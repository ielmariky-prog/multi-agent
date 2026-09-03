package io.multiagent.core.model;

import lombok.Data;

@Data
public class WorkflowDecision {

    private String eventType;
    private String rationale;
    private String originalText;
    private double confidence;
    private String targetQueue;
    private String timestamp;
}
