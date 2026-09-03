package io.multiagent.core.service;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.multiagent.core.client.LLMAIClient;
import io.multiagent.core.model.IntentResult;
import io.multiagent.core.exception.LLMClientException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntentClassifierService {

    private final LLMAIClient openai;
    private final ObjectMapper mapper = new ObjectMapper();

    private String systemPrompt;
    private String examplesPrompt;
    @Value("${AI_CORE_PROMPT_CLASSIFIER_SYSTEM:}")
    private String systemPromptEnv;
    @Value("${AI_CORE_PROMPT_CLASSIFIER_EXAMPLES:}")
    private String examplesPromptEnv;

    @PostConstruct
    public void loadPrompts() {
        try {
            systemPrompt = resolvePrompt(systemPromptEnv, "prompts/prompt_classifier_system.txt");
            examplesPrompt = resolvePrompt(examplesPromptEnv, "prompts/prompt_classifier_examples.txt");
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger les prompts du classifieur", e);
        }
    }

    private String resolvePrompt(String envValue, String fallbackFile) throws Exception {
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return loadFromClasspath(fallbackFile);
    }

    private String loadFromClasspath(String file) throws Exception {
        ClassPathResource res = new ClassPathResource(file);
        try (var in = res.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    public IntentResult classify(String userText) {

        try {
            String fullPrompt = """
                %s

                EXAMPLES:
                %s

                USER: %s
                """.formatted(systemPrompt, examplesPrompt, userText);

            // 🟢 version compatible avec TA méthode extractJSON()
            String json = openai.extractJSON(systemPrompt, fullPrompt);
            log.info("classify: {}",json);
            JsonNode node = mapper.readTree(json);

            IntentResult r = new IntentResult();
            r.setIntent(node.path("intent").asText("unknown"));
            r.setConfidence(node.path("confidence").asDouble());
            r.setExplanation(node.path("explanation").asText());
            r.setOriginalText(userText);
            if (node.has("entities")) {
                r.setEntities(mapper.convertValue(node.get("entities"), java.util.Map.class));
            }

            return r;

        } catch (LLMClientException ex) {
            // Cas fréquent: rate limit / quota → on renvoie un fallback sans stacktrace
            log.warn("⚠️ Intent classification fallback (LLM error): {}", ex.getMessage());
            String info = "LLM issue (" + ex.getMessage() + "), model=" + openai.getLlmModel();
            return fallback(userText, info);
        } catch (Exception e) {
            log.error("❌ Intent classification error", e);
            return fallback(userText, "Erreur JSON-mode / OpenAI");
        }
    }

    private IntentResult fallback(String userText, String explanation) {
        IntentResult fallback = new IntentResult();
        fallback.setIntent("error");
        fallback.setConfidence(0.0);
        fallback.setExplanation(explanation);
        fallback.setOriginalText(userText);
        return fallback;
    }
}
