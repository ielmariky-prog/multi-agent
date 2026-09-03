package io.multiagent.intent.kafka;

import io.multiagent.intent.dto.IntentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentKafkaProducer {

    private final KafkaTemplate<String, IntentDTO> kafkaTemplate;

    @Value("${spring.kafka.topics.intent-output}")
    private String outputTopic;

    public void publish(IntentDTO dto) {
        kafkaTemplate.send(outputTopic, dto);
        log.info("📤 [Intent-Agent] Intent envoyé dans topic={} : {}", outputTopic, dto.getIntent());
    }
}
