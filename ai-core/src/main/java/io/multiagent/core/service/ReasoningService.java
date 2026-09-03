package io.multiagent.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.multiagent.core.model.IntentResult;
import io.multiagent.core.model.ReasoningResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReasoningService {

    private final IntentClassifierService intentClassifierService;
    private final RAGService ragService;
    private final DeleteExpenseService deleteExpenseService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReasoningResult process(String userText) {
        IntentResult intent = tryParseIntent(userText);
        String raw = userText;
        if (intent == null) {
            intent = intentClassifierService.classify(userText);
            raw = userText;
        } else {
            raw = intent.getOriginalText() != null ? intent.getOriginalText() : userText;
        }
        log.info("🎯 Intent détecté: {}", intent.getIntent());

        return switch (intent.getIntent()) {

            // 🔵 Pipeline création d’une note de frais
            case "create_expense" -> ragService.extractSingleExpense(raw, intent);

            // 🟢 Pipeline génération d’un rapport
            case "generate_expense_report" -> ragService.extractExpenseReport(raw, intent);

            // 🔵 Pipeline génération de facture
            case "generate_invoice" -> ragService.extractInvoiceData(raw, intent);

            // 🔴 Suppression d’une note de frais par date
            case "delete_expense" -> deleteExpenseService.deleteByText(raw, intent);

            // 🟡 Smalltalk = réponse non métier
            case "smalltalk" -> ReasoningResult.smalltalk("Je suis un agent métier, pas un chatbot général.");

            // 🔴 Intent inconnu
            default -> ReasoningResult.builder()
                    .type("unknown")
                    .confidence(intent.getConfidence())
                    .raw(raw)
                    .metadata(null)
                    .expenses(null)
                    .build();
        };
    }

    private IntentResult tryParseIntent(String text) {
        try {
            JsonNode node = mapper.readTree(text);
            if (node.has("intent")) {
                IntentResult r = new IntentResult();
                r.setIntent(node.path("intent").asText("unknown"));
                r.setConfidence(node.path("confidence").asDouble());
                r.setExplanation(node.path("explanation").asText());
                // Priorité à originalText si présent
                if (node.has("originalText")) {
                    r.setOriginalText(node.get("originalText").asText());
                } else if (node.has("text")) {
                    r.setOriginalText(node.get("text").asText());
                }
                if (node.has("entities")) {
                    r.setEntities(mapper.convertValue(node.get("entities"), java.util.Map.class));
                }
                return r;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
