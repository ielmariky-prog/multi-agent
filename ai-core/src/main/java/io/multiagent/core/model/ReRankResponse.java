package io.multiagent.core.model;

import lombok.Data;
import java.util.List;

/**
 * Représente la réponse d'un appel ReRank de OpenAI.
 */
@Data
public class ReRankResponse {
    private List<ReRankResult> results;
}