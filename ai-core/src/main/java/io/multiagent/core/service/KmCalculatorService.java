package io.multiagent.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.multiagent.core.client.LLMAIClient;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Calcule les frais kilométriques via le prompt LLM (AI_CORE_PROMPT_KM_7CV).
 */
@Service
@Slf4j
public class KmCalculatorService {

    private final LLMAIClient llm;
    private final String kmPromptTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public KmCalculatorService(LLMAIClient llm, @org.springframework.beans.factory.annotation.Value("${AI_CORE_PROMPT_KM_7CV:}") String kmPromptTemplate) {
        this.llm = llm;
        if (kmPromptTemplate == null || kmPromptTemplate.isBlank()) {
            throw new IllegalStateException("AI_CORE_PROMPT_KM_7CV doit être défini via la ConfigMap/ENV");
        }
        this.kmPromptTemplate = kmPromptTemplate;
    }

    public KmResult compute(double kilometrageAnnuel, double kilometrageJournalier, int cv, String typeVehicule, String anneeBareme) {
        try {
            String systemPrompt = kmPromptTemplate + "\n\nRéponds uniquement en JSON strict du type : {\n" +
                    "  \"annualCost\": <number>,\n" +
                    "  \"dailyCost\": <number>,\n" +
                    "  \"monthlyCost\": <number>,\n" +
                    "  \"formula\": \"...\",\n" +
                    "  \"warning\": \"...\"\n" +
                    "}";
            String userPrompt = """
                    {
                      "kilometrage_annuel": %s,
                      "kilometrage_journalier": %s,
                      "cv": %s,
                      "type_vehicule": "%s",
                      "annee_bareme": "%s"
                    }
                    """.formatted(kilometrageAnnuel, kilometrageJournalier, cv, typeVehicule, anneeBareme == null ? "" : anneeBareme);

            String json = llm.extractJSON(systemPrompt, userPrompt);
            return parse(json);
        } catch (Exception e) {
            log.warn("⚠️ KmCalculatorService fallback (LLM erreur) : {}", e.getMessage());
            return KmResult.builder()
                    .annualCost(0.0)
                    .dailyCost(0.0)
                    .monthlyCost(0.0)
                    .formula("fallback")
                    .warning(e.getMessage())
                    .build();
        }
    }

    private KmResult parse(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return KmResult.builder()
                    .annualCost(node.path("annualCost").asDouble(0.0))
                    .dailyCost(node.path("dailyCost").asDouble(0.0))
                    .monthlyCost(node.path("monthlyCost").asDouble(0.0))
                    .formula(node.path("formula").asText(null))
                    .warning(node.path("warning").asText(null))
                    .build();
        } catch (Exception e) {
            log.warn("⚠️ KmCalculatorService parse error : {}", e.getMessage());
            return KmResult.builder()
                    .annualCost(0.0)
                    .dailyCost(0.0)
                    .monthlyCost(0.0)
                    .formula("parse_error")
                    .warning(e.getMessage())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class KmResult {
        private final double annualCost;
        private final double dailyCost;
        private final double monthlyCost;
        private final String formula;
        private final String warning;
    }
}
