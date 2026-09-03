package io.multiagent.reasoning.kafka;

import io.multiagent.reasoning.dto.ReasoningAnalysisDTO;
import io.multiagent.reasoning.service.ReasoningAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes intent outputs from Kafka and triggers the Reasoning-Agent workflow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReasoningKafkaListener {

    private final ReasoningAgentService reasoning;
    private final ReasoningKafkaProducer producer;

    @KafkaListener(topics = "${spring.kafka.topics.reasoning-input}")
    public void onMessage(String text) {

        log.info("📥 Message reçu par Reasoning-Agent : {}", text);

        producer.publishRawDocument(text);
        ReasoningAnalysisDTO rr = reasoning.analyze(text);
        producer.publish(rr); // envoie dans reasoning-output-topic
    }
}
