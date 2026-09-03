package io.multiagent.core.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * Spécifique au domaine « intent » : il s’appuie sur RAGService pour produire une classification. 
 * Il construit un prompt demandant d’identifier l’intention parmi un ensemble prédéfini et 
 * le passe au LLM pour obtenir « expense », « cancel », « travel »…
 */


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.util.*;

public class IntentAgentService {

   // private final OpenAIClient openAIClient;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public IntentAgentService( String model) {
        //this.openAIClient = openAIClient;
        this.model = model;
    }

   /** public IntentResult classify(String input) {
        try {
            List<Message> messages = new ArrayList<>();

           messages.add(new Message("system",
                    "You are a business intent classification engine for an HR & Expense Automation System. " +
                    "Detect the user intent and extract structured entities. " +
                    "Return ONLY valid JSON with this strict structure:\n" +
                    "{\n" +
                    "  \"input\": \"...\",\n" +
                    "  \"intent\": \"...\",\n" +
                    "  \"confidence\": 0.0,\n" +
                    "  \"entities\": { \"...\": \"...\" }\n" +
                    "}\n\n" +
                    
                    "ALLOWED INTENTS (domain-specific):\n" +
                    "- \"GENERATE_EXPENSE_REPORT\"\n" +
                    "- \"ADD_EXPENSE_ITEM\"\n" +
                    "- \"GENERATE_PAYSLIP\"\n" +
                    "- \"SHOW_EXPENSES\"\n" +
                    "- \"ASK_BALANCE\"\n" +
                    "- \"REQUEST_DOCUMENT\"\n" +
                    "- \"UNKNOWN\"\n\n" +

                    "Rules:\n" +
                    "- If unsure, return intent = \"UNKNOWN\".\n" +
                    "- Confidence must be between 0 and 1.\n" +
                    "- Entities should capture structured values such as amount, date, city, period, category, employee name, etc.\n"
            ));

         //   messages.add(new Message("user", input));

            String json = openAIClient.generate(model, messages, 0.0);
            JsonNode root = mapper.readTree(json);

            String parsedInput = root.has("input") ? root.get("input").asText() : input;
            String intent = root.get("intent").asText();
            double confidence = root.get("confidence").asDouble();

            Map<String, String> entities = new HashMap<>();
            if (root.has("entities")) {
                root.get("entities").fields().forEachRemaining(e -> {
                    entities.put(e.getKey(), e.getValue().asText());
                });
            }

            return new io.multiagent.core.model.IntentResult(parsedInput, intent, confidence, entities);

        } catch (Exception e) {
            throw new RuntimeException("IntentAgentService.classify() error", e);
        }
            
    }

    public String classifyIntentName(String input) {
        return classify(input).getIntent();
    }
        **/
}