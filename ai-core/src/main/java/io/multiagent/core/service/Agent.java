package io.multiagent.reassign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
public class Agent {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final WebClient aiCoreClient;
    private final String auditTopic;
    private final ObjectMapper objectMapper;
    private final Duration aiCoreTimeout;

    public Agent(
            KafkaTemplate<String, String> kafkaTemplate,
            WebClient.Builder webClientBuilder,
            @Value("${AI_CORE_URL:http://ai-core:8081}") String aiCoreUrl,
            @Value("${REASSIGN_OUTPUT_TOPIC:audit.events.in}") String auditTopic,
            @Value("${AI_CORE_TIMEOUT:15s}") Duration aiCoreTimeout,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.aiCoreClient = webClientBuilder.baseUrl(aiCoreUrl).build();
        this.auditTopic = auditTopic;
        this.objectMapper = objectMapper;
        this.aiCoreTimeout = aiCoreTimeout;
    }

    @KafkaListener(topics = "${REASSIGN_INPUT_TOPIC:reassign-input-topic}", groupId = "reassign-agent")
    public void processMessage(String reasoningResultJson) {
        System.out.println("Reassign-Agent received: " + reasoningResultJson);
        try {
            JsonNode root = objectMapper.readTree(reasoningResultJson);
            String type = root.path("type").asText();

            if ("generate_invoice".equals(type)) {
                handleInvoiceGeneration(root);
            } else {
                System.out.println("Reassign-Agent: type '" + type + "' not handled for action, forwarding to audit.");
                kafkaTemplate.send(auditTopic, reasoningResultJson);
            }
        } catch (Exception e) {
            System.err.println("Reassign-Agent: Failed to process message. " + e.getMessage());
            kafkaTemplate.send(auditTopic, "{\"error\":\"" + e.getMessage() + "\", \"originalMessage\":" + reasoningResultJson + "}");
        }
    }

    private void handleInvoiceGeneration(JsonNode reasoningResult) {
        JsonNode metadata = reasoningResult.path("metadata");
        if (!metadata.has("invoiceDataJson")) {
            System.err.println("Reassign-Agent: 'invoiceDataJson' missing in metadata for generate_invoice.");
            return;
        }

        String invoiceRequestJson = metadata.get("invoiceDataJson").asText();
        System.out.println("Reassign-Agent: Triggering PDF generation with data: " + invoiceRequestJson);

        aiCoreClient.post().uri("/invoices/generate").header("Content-Type", "application/json").bodyValue(invoiceRequestJson).retrieve().bodyToMono(String.class)
                .timeout(aiCoreTimeout)
                .doOnSuccess(response -> kafkaTemplate.send(auditTopic, reasoningResult.toString()))
                .doOnError(error -> System.err.println("Error calling AI-Core for invoice generation: " + error.getMessage())).subscribe();
    }
}