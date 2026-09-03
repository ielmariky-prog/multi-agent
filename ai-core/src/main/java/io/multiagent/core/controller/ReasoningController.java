package io.multiagent.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.multiagent.core.model.ReasoningResult;
import io.multiagent.core.service.ReasoningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/reasoning")
public class ReasoningController {

    private final ReasoningService reasoningService;
    private final ObjectMapper objectMapper;

    @PostMapping("/analyze")
    public ReasoningResult analyze(@RequestBody String payload) {
        String normalizedPayload = normalizePayload(payload);
        log.info("➡️ AI-Core /reasoning/analyze received payload ({} chars) normalized to {} chars",
                payload.length(), normalizedPayload.length());
        return reasoningService.process(normalizedPayload);
    }

    private String normalizePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!node.isObject()) {
                return payload;
            }
            // Cas 1: requête simple de type {"text":"..."} -> on extrait le texte métier.
            if (node.hasNonNull("text") && !node.has("intent")) {
                return node.get("text").asText(payload);
            }
            // Cas 2: JSON d'intent déjà structuré -> on le garde tel quel pour préserver entities/ids/month.
            return payload;
        } catch (Exception ignored) {
            return payload;
        }
    }
}
