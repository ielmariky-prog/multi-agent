package io.multiagent.core.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExpenseReportResponse {
    private LocalDate start;
    private LocalDate end;
    private String company;
    private int count;
    private List<ExpenseItem> expenses;
    private Map<String, Double> totalsByCurrency;
    private Map<String, Double> totalsByType;
}
