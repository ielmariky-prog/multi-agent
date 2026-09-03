package io.multiagent.reassign.service;

import io.multiagent.core.model.AssignmentResult;
import io.multiagent.core.model.ExpenseItem;
import io.multiagent.core.model.ReasoningResult;
import io.multiagent.reassign.dto.AssignmentDTO;
import io.multiagent.reassign.dto.ReasoningMessageDTO;
import io.multiagent.reassign.dto.WorkflowEventDTO;
import io.multiagent.reassign.kafka.WorkflowEventProducer;
import io.multiagent.reassign.mapper.ReassignMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReassignAgentService {

    private final WebClient.Builder http;
    private final ReassignMapper mapper;
    private final KafkaTemplate<String, AssignmentDTO> kafkaTemplate;
    private final WorkflowEventProducer workflowEventProducer;

    @Value("${ai-core.url}")
    private String aiCoreUrl;

    @Value("${kafka.reassign.output-topic}")
    private String outputTopic;

    @Value("${ai-core.timeout-ms:5000}")
    private long aiCoreTimeoutMs;

    /**
     * Reçoit un ReasoningMessageDTO, appelle AI-Core /reassign/assign,
     * mappe le résultat et publie l'AssignmentDTO dans Kafka.
     */
    public AssignmentDTO processAndPublish(ReasoningMessageDTO msg) {

        log.info("🔁 [Reassign-Agent] Traitement ReasoningMessage – status={}, confidence={}",
                msg.getStatus(), msg.getConfidence());

        if ("INVOICE_DATA_EXTRACTED".equals(msg.getStatus())) {
            return generateInvoice(msg);
        }

        ReasoningResult payload = toReasoningResult(msg);

        // Appel AI-Core
        AssignmentResult result = http.build()
                .post()
                .uri(aiCoreUrl + "/reassign/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)  // JSON ReasoningResult attendu par AI-Core
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalStateException(
                                        "AI-Core error %s: %s".formatted(response.statusCode(), body))))
                .bodyToMono(AssignmentResult.class)
                .timeout(Duration.ofMillis(aiCoreTimeoutMs))
                .block();

        if (result == null) {
            log.error("❌ AI-Core /reassign/assign a renvoyé null.");
            AssignmentDTO fallback = new AssignmentDTO();
            fallback.setStatus("ERROR");
            fallback.setAssigneeType("NONE");
            fallback.setReason("AI-Core n'a pas répondu.");
            fallback.setConfidence(0.0);
            fallback.setOriginalText(msg.getOriginalText());
            return fallback;
        }

        // Mapping vers AssignmentDTO
        AssignmentDTO dto = mapper.toDTO(result);
        dto.setOriginalText(msg.getOriginalText());

        // Publication dans Kafka
        kafkaTemplate.send(outputTopic, dto);

        log.info("📤 [Reassign-Agent] Assignment publié dans topic={} : type={}, status={}",
                outputTopic, dto.getAssigneeType(), dto.getStatus());

        String correlationId = extractCorrelationId(msg);

        if (correlationId != null) {
            workflowEventProducer.publish(
                    WorkflowEventDTO.builder()
                            .correlationId(correlationId)
                            .decision(msg.getStatus() != null ? msg.getStatus() : dto.getStatus())
                            .timestamp(Instant.now())
                            .build()
            );
        } else {
            log.warn("⚠️ correlationId absent, skip workflowEvent publish");
        }

        return dto;
    }

    private AssignmentDTO generateInvoice(ReasoningMessageDTO msg) {
        Object invoiceDataJson = msg.getMetadata() == null ? null : msg.getMetadata().get("invoiceDataJson");
        if (invoiceDataJson == null || invoiceDataJson.toString().isBlank()) {
            AssignmentDTO error = new AssignmentDTO();
            error.setStatus("ERROR");
            error.setAssigneeType("NONE");
            error.setReason("invoiceDataJson absent pour la génération de facture.");
            error.setConfidence(0.0);
            error.setOriginalText(msg.getOriginalText());
            return error;
        }

        Map<?, ?> response = http.build()
                .post()
                .uri(aiCoreUrl + "/invoices/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(invoiceDataJson.toString())
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        res -> res.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalStateException(
                                        "AI-Core invoice generation error %s: %s".formatted(res.statusCode(), body))))
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(aiCoreTimeoutMs))
                .block();

        AssignmentDTO dto = new AssignmentDTO();
        dto.setOriginalText(msg.getOriginalText());
        if (response == null) {
            dto.setStatus("ERROR");
            dto.setAssigneeType("NONE");
            dto.setReason("AI-Core n'a pas répondu pour la génération de facture.");
            dto.setConfidence(0.0);
            return dto;
        }

        dto.setStatus("GENERATED");
        dto.setAssigneeType("NONE");
        dto.setReason("Facture générée et indexée.");
        dto.setConfidence(msg.getConfidence());
        kafkaTemplate.send(outputTopic, dto);

        log.info("📤 [Reassign-Agent] Facture générée via AI-Core /invoices/generate");

        String correlationId = extractCorrelationId(msg);
        if (correlationId != null) {
            workflowEventProducer.publish(
                    WorkflowEventDTO.builder()
                            .correlationId(correlationId)
                            .decision("INVOICE_GENERATED")
                            .timestamp(Instant.now())
                            .build()
            );
        } else {
            log.warn("⚠️ correlationId absent pour la facture, skip workflowEvent publish");
        }

        return dto;
    }

    private ReasoningResult toReasoningResult(ReasoningMessageDTO msg) {
        ReasoningResult rr = new ReasoningResult();
        rr.setStatus(msg.getStatus());
        rr.setConfidence(msg.getConfidence() == null ? 0.0 : msg.getConfidence());
        rr.setRaw(msg.getOriginalText());
        rr.setMetadata(msg.getMetadata());

        if (msg.getExpenses() != null) {
            rr.setExpenses(msg.getExpenses().stream()
                    .map(e -> {
                        ExpenseItem item = new ExpenseItem();
                        item.setAmount(e.getAmount());
                        item.setCurrency(e.getCurrency());
                        item.setType(e.getType());
                        item.setDate(e.getDate());
                        item.setDescription(e.getDescription());
                        item.setOriginalText(e.getOriginalText());
                        return item;
                    })
                    .toList());
        }
        return rr;
    }

    private String extractCorrelationId(ReasoningMessageDTO msg) {
        if (msg.getMetadata() == null) {
            return null;
        }
        Object raw = msg.getMetadata().get("correlationId");
        return raw == null ? null : raw.toString();
    }
}
