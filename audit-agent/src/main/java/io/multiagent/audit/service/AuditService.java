package io.multiagent.audit.service;

import org.springframework.stereotype.Service;

import io.multiagent.audit.dto.AuditEventDTO;
import io.multiagent.audit.dto.AuditForwardEventDTO;
import io.multiagent.audit.kafka.AuditEventProducer;
import io.multiagent.audit.mapper.AuditEventMapper;


@Service
public class AuditService {

    private final AuditEventMapper mapper;
    private final AuditEventProducer producer;

    public AuditService(
            AuditEventMapper mapper,
            AuditEventProducer producer
    ) {
        this.mapper = mapper;
        this.producer = producer;
    }

    public void process(AuditEventDTO event) {

        AuditForwardEventDTO forward = mapper.toEnriched(event);
        producer.send(forward);
    }
}