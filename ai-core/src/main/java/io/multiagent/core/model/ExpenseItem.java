package io.multiagent.core.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Représente une dépense élémentaire extraite par le LLM
 * (pour une note de frais ou un rapport).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseItem {

    /** Identifiant incrémental (réinitialisé chaque mois) */
    private Integer id;

    /** Montant de la dépense (ex : 100.0) */
    private Double amount;

    /** Devise (ex : "EUR") */
    private String currency;

    private String status;
    
    /** Type de dépense (ex : "restaurant", "hotel", "taxi"…) */
    private String type;

    /** Kilométrage (pour frais km) */
    private Double km;

    /** Date au format ISO (ex : "2025-02-20") */
    private String date;

    /** Description courte (ex : "Repas au restaurant avec client") */
    private String description;

    /** Texte d’origine dont est issue cette dépense (optionnel mais utile pour audit) */
    private String originalText;

    /** Mode de paiement (Personnel | Business) si disponible */
    private String paymentMode;

    /** Adresse/lieu si présent dans le justificatif */
    private String address;

    /** Société/organisation concernée par la note de frais */
    private String company;

    // --------------------------------------------------------------------
    // Helpers pour parser la réponse JSON du LLM
    // --------------------------------------------------------------------

    /**
     * Parse un tableau JSON en liste de ExpenseItem.
     * Exemple de JSON attendu :
     * [
     *   { "amount": 100, "currency": "EUR", "type": "restaurant", "date": "2025-02-20", "description": "Repas" }
     * ]
     */
    public static List<ExpenseItem> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<List<ExpenseItem>>() {});
        } catch (Exception e) {
            // En prod tu peux logger l’erreur proprement
            return Collections.emptyList();
        }
    }
}
