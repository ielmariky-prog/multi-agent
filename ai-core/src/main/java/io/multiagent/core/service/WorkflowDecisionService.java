package io.multiagent.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.multiagent.core.client.LLMAIClient;
import io.multiagent.core.model.ReasoningResult;
import io.multiagent.core.model.WorkflowDecision;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Converts reasoning results into concrete workflow decisions by prompting the LLM,
 * enforcing JSON structure, and filling defaults so downstream agents can route actions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDecisionService {

    private final LLMAIClient llm;
    private final ObjectMapper mapper;

    @Value("${ai-core.openai.workflow-model:gpt-4o-mini}")
    private String workflowModel;

    public WorkflowDecision decide(String reasoningPayload) {
        ReasoningResult reasoning = deserializeReasoning(reasoningPayload);

        String rawJson = llm.chatJson(
                workflowModel,
                workflowSystemPrompt(),
                workflowUserPrompt(reasoning));
        WorkflowDecision decision = parseDecision(rawJson);

        if (decision.getOriginalText() == null || decision.getOriginalText().isBlank()) {
            decision.setOriginalText(reasoning.getExpenses().get(0).getOriginalText());
        }
        if (decision.getTimestamp() == null || decision.getTimestamp().isBlank()) {
            decision.setTimestamp(Instant.now().toString());
        }
        if (decision.getRationale() == null || decision.getRationale().isBlank()) {
            decision.setRationale(reasoning.getExpenses().get(0).getDescription());
        }

        log.info("⚙️ Workflow decision computed: eventType={} targetQueue={} confidence={}",
                decision.getEventType(), decision.getTargetQueue(), decision.getConfidence());
        return decision;
    }

    private ReasoningResult deserializeReasoning(String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("reasoningPayload vide");
            }
            return mapper.readValue(payload, ReasoningResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de lire le reasoningPayload", e);
        }
    }

    private WorkflowDecision parseDecision(String json) {
        try {
            if (json == null || json.isBlank()) {
                throw new IllegalStateException("Réponse workflow vide");
            }
            return mapper.readValue(json, WorkflowDecision.class);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de parser la décision workflow", e);
        }
    }

    private String workflowUserPrompt(ReasoningResult reasoning) {
        return """
                Analyse le résultat reasoning JSON suivant et décide de la prochaine action workflow :

                %s

                Rappelle-toi : retourne uniquement le JSON demandé.
                """.formatted(writeReasoningJson(reasoning));
    }

    private String workflowSystemPrompt() {
        return """
                Tu es l'orchestrateur workflow d'une plateforme RH/Expense.
                À partir d'un reasoning structuré, tu dois choisir la prochaine action et son routage.

                Renvoie un JSON strict avec les champs :
                {
                  "eventType": "ACTION_IDENTIFIER",
                  "targetQueue": "kafka/topic/or/service",
                  "rationale": "explication courte",
                  "confidence": 0.0-1.0,
                  "originalText": "texte brut",
                  "timestamp": "ISO-8601"
                }

                Contraintes :
                - choisit eventType parmi: "CREATE_EXPENSE", "REQUEST_DETAILS", "ROUTE_TO_HUMAN", "VALIDATE_DATA", "UNKNOWN".
                - targetQueue = composant ou file cible (ex: "workflow.expense.create").
                - timestamp : ajoute `Instant.now()` si absent.
                - Réponds en JSON strict sans texte additionnel.
                """;
    }

    private String writeReasoningJson(ReasoningResult reasoning) {
        try {
            return mapper.writeValueAsString(reasoning);
        } catch (Exception e) {
            return """
                    {
                      "originalText": "%s",
                      "raw": "%s",
                      "explanation": "%s",
                      "confidence": %s
                    }
                    """.formatted(
                            safe(reasoning.getExpenses().get(0).getOriginalText()),
                            safe(reasoning.getRaw()),
                            safe(reasoning.getExpenses().get(0).getDescription()),
                            reasoning.getConfidence());
        }
    }

    private String safe(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "'");
    }
}
