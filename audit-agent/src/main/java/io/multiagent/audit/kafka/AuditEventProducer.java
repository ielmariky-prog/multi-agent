package io.multiagent.audit.kafka;

import io.multiagent.audit.dto.AuditForwardEventDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditEventProducer {

    private final KafkaTemplate<String, AuditForwardEventDTO> kafkaTemplate;
    private final String outputTopic;

    public AuditEventProducer(
            KafkaTemplate<String, AuditForwardEventDTO> kafkaTemplate,
            @Value("${spring.kafka.topics.audit-output}") String outputTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.outputTopic = outputTopic;
    }

    public void send(AuditForwardEventDTO event) {
        kafkaTemplate.send(
                outputTopic,
                event.getCorrelationId(),
                event
        );
    }
}
