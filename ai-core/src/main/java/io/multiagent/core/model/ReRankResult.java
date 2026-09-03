package io.multiagent.core.model;

import lombok.Data;

/**
 * Élément individuel du résultat de reranking.
 */
@Data
public class ReRankResult {
    private String document;     // le texte du chunk
    private double relevance;    // score de pertinence renvoyé par OpenAI
    private int index;           // position originale du chunk dans la liste des candidats
}