package io.multiagent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntentResult {

    /**
     * Intent détecté :
     * - "create_expense"
     * - "generate_expense_report"
     * - "smalltalk"
     * - "unknown"
     * - "error"
     */
    private String intent;

    /**
     * Score de confiance ∈ [0.0 , 1.0]
     */
    private double confidence;

    /**
     * Courte explication retournée par l'IA
     */
    private String explanation;

    /**
     * Le texte utilisateur brut fourni à l'analyse
     */
    private String originalText;

    /**
     * Entités optionnelles (ex: ids à supprimer, date, etc.) renvoyées par le classifieur.
     */
    private java.util.Map<String, Object> entities;
}
