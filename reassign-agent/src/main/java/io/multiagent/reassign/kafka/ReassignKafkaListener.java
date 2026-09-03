package io.multiagent.reassign.kafka;

import io.multiagent.reassign.dto.ReasoningMessageDTO;
import io.multiagent.reassign.service.ReassignAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReassignKafkaListener {

    private final ReassignAgentService service;

    @KafkaListener(
            topics = "${kafka.reassign.input-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onReasoningMessage(ReasoningMessageDTO msg) {
        log.info("📥 [Reassign-Agent] Message Reasoning reçu depuis Kafka, status={}", msg.getStatus());
        service.processAndPublish(msg);
    }
}