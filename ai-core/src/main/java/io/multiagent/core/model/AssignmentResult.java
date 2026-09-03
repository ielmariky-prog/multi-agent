package io.multiagent.core.model;

import lombok.Data;
                     
@Data
public class AssignmentResult {

    /**
     * Qui doit traiter cette note de frais ?
     * Exemple : "FINANCE", "MANAGER", "HR", "SYSTEM", "NONE"
     */
    private String assigneeType;

    /**
     * Identifiant éventuel (id du manager, id de l'équipe finance...).
     * Peut être null.
     */
    private String assigneeId;

    /**
     * Statut de l'assignation :
     * - "ASSIGNED"
     * - "SKIPPED"
     * - "ERROR"
     */
    private String status;

    /**
     * Raison textuelle, courte.
     */
    private String reason;

    /**
     * Confiance de la décision (0.0 - 1.0).
     */
    private Double confidence;
}