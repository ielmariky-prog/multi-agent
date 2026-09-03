package io.multiagent.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReRankScore {
    private int index;
    private double score;
}