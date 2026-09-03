package io.multiagent.reassign.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ReasoningMessageDTO {

    private String status;
    private Double confidence;
    private String answer;
    private String explanation;
    private String originalText;

    private List<ExpenseItemDTO> expenses;
    private Map<String, Object> metadata;
}