package io.multiagent.audit.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import io.multiagent.audit.dto.AuditEventDTO;
import io.multiagent.audit.service.AuditService;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {

    private final AuditService auditService;

    public AuditEventConsumer(AuditService auditService) {
        this.auditService = auditService;
    }

    @KafkaListener(
            topics = "${spring.kafka.topics.audit-input}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(AuditEventDTO event) {
        auditService.process(event);
    }
}
