package io.multiagent.reasoning.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReasoningAnalysisDTO {

    private String type;
    private String status;
    private Double confidence;
    private String answer;
    private String explanation;
    private String originalText;
    private String raw;

    private List<ExpenseItemDTO> expenses;
    private Map<String, Object> metadata;
}
