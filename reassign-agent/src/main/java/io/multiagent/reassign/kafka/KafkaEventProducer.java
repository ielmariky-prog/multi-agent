package io.multiagent.reassign.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(String topic, String correlationId, Object payload) {

        ProducerRecord<String, Object> record =
                new ProducerRecord<>(topic, correlationId, payload);

        // Header standard pour tracing distribué
        record.headers().add(
                "X-Correlation-Id",
                correlationId.getBytes(StandardCharsets.UTF_8)
        );

        kafkaTemplate.send(record);
    }
}
