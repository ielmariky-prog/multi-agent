package io.multiagent.reasoning.kafka;

import io.multiagent.reasoning.dto.ReasoningAnalysisDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReasoningKafkaProducer {

    private final KafkaTemplate<String, ReasoningAnalysisDTO> reasoningKafka;
    private final KafkaTemplate<String, String> rawKafka;

    @Value("${spring.kafka.topics.reasoning-output}")
    private String outputTopic;
    @Value("${spring.kafka.topics.documents-raw}")
    private String documentsTopic;

    public void publish(ReasoningAnalysisDTO rr) {
        reasoningKafka.send(outputTopic, rr);
        log.info("📤 Reasoning-Agent → envoyé vers {}", outputTopic);
    }

    public void publishRawDocument(String text) {
        rawKafka.send(documentsTopic, text);
        log.info("🗂️ Reasoning-Agent → document envoyé vers {}", documentsTopic);
    }
}
