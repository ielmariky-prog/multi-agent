package io.multiagent.core.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Écoute workflow-output-topic (= reassign-output-topic).
 * Quand le WorkflowEventDTO arrive, débloque le submitAndWait correspondant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowResultListener {

    private final PipelineOrchestrationService orchestrationService;
    private final ObjectMapper mapper;

    @KafkaListener(
            topics = "${spring.kafka-topics.workflow-output:reassign-output-topic}",
            groupId = "ai-core-pipeline"
    )
    public void onWorkflowResult(String message) {
        try {
            Map<String, Object> result = mapper.readValue(
                    message, new TypeReference<Map<String, Object>>() {});
            String correlationId = (String) result.get("correlationId");
            if (correlationId != null) {
                orchestrationService.resolve(correlationId, result);
            } else {
                log.warn("⚠️ WorkflowResultListener — message sans correlationId, ignoré");
            }
        } catch (Exception e) {
            log.error("❌ WorkflowResultListener — erreur parsing message", e);
        }
    }
}
