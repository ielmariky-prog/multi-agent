package io.multiagent.core.service;

import io.multiagent.core.model.AssignmentResult;
import io.multiagent.core.model.ExpenseItem;
import io.multiagent.core.model.ReasoningResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ReassignService {

    /**
     * Décide où envoyer la demande en fonction du ReasoningResult.
     */
    public AssignmentResult assign(ReasoningResult rr) {

        AssignmentResult res = new AssignmentResult();

        if (rr == null) {
            res.setStatus("ERROR");
            res.setReason("ReasoningResult null");
            res.setAssigneeType("NONE");
            res.setConfidence(0.0);
            return res;
        }

        String status = rr.getStatus();
        List<ExpenseItem> expenses = rr.getExpenses();

        double total = (expenses != null)
                ? expenses.stream()
                          .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0.0)
                          .sum()
                : 0.0;

        // Exemple de règles métiers simples
        if ("EXPENSE_CREATED".equals(status)) {
            if (total > 1000.0) {
                res.setAssigneeType("MANAGER");
                res.setAssigneeId(null); // plus tard : id du manager
                res.setStatus("ASSIGNED");
                res.setReason("Montant total > 1000 EUR, validation manager requise.");
                res.setConfidence(0.9);
            } else {
                res.setAssigneeType("FINANCE");
                res.setAssigneeId(null); // plus tard : id du service finance
                res.setStatus("ASSIGNED");
                res.setReason("Montant raisonnable, route vers la finance.");
                res.setConfidence(0.95);
            }
        } else if ("EXPENSE_REPORT".equals(status)) {
            res.setAssigneeType("FINANCE");
            res.setAssigneeId(null);
            res.setStatus("ASSIGNED");
            res.setReason("Rapport de dépenses complet, route vers la finance.");
            res.setConfidence(0.9);
        } else if ("SMALLTALK".equals(status)) {
            res.setAssigneeType("NONE");
            res.setStatus("SKIPPED");
            res.setReason("Smalltalk, aucune action métier.");
            res.setConfidence(0.99);
        } else {
            res.setAssigneeType("NONE");
            res.setStatus("ERROR");
            res.setReason("Statut non géré: " + status);
            res.setConfidence(0.2);
        }

        log.info("🎯 ReassignService → assigneeType={}, status={}, total={}",
                res.getAssigneeType(), res.getStatus(), total);

        return res;
    }
}