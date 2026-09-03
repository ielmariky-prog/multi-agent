package io.multiagent.core.controller;

import io.multiagent.core.pipeline.PipelineOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineOrchestrationService pipeline;

    /**
     * Soumet un texte dans la chaîne complète des agents Kafka et attend le résultat.
     * Corps JSON attendu : {"text": "...", "timeoutMs": 30000}
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@RequestBody Map<String, Object> body) {
        String text = (String) body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", "Le champ 'text' est obligatoire."));
        }
        long timeoutMs = body.containsKey("timeoutMs")
                ? ((Number) body.get("timeoutMs")).longValue()
                : 30_000L;

        Map<String, Object> result = pipeline.submitAndWait(text, timeoutMs);
        return ResponseEntity.ok(result);
    }
}
