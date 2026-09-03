package io.multiagent.reassign.dto;

import lombok.Data;

@Data
public class ExpenseItemDTO {
    private Double amount;
    private String currency;
    private String description;
    private String type;
    private String date;
    private String originalText;
}