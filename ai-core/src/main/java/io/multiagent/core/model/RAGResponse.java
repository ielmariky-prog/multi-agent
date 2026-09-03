package io.multiagent.core.model;

import lombok.Data;

import java.util.List;

@Data
public class RAGResponse {

    /** Question d'origine envoyée par l'utilisateur */
    private String originalQuery;

    /** Question réécrite (rewrite) utilisée pour la recherche */
    private String rewrittenQuery;

    /** Réponse finale générée par le LLM */
    private String answer;

    /** Confiance estimée par le modèle (0.0 → 1.0) */
    private double confidence;

    /** Raisonnement / explication naturelle */
    private String reasoning;

    /** Contexte textuel final utilisé (concat des chunks) */
    private String contextUsed;

    /** Liste des chunks utilisés pour construire la réponse */
    private List<RAGChunk> chunks;
}