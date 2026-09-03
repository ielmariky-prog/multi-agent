package io.multiagent.reassign.kafka;

import io.multiagent.reassign.dto.WorkflowEventDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEventProducer {

    private final KafkaEventProducer kafkaEventProducer;
    private final String workflowTopic;

    public WorkflowEventProducer(
            KafkaEventProducer kafkaEventProducer,
            @Value("${app.kafka.topics.workflow}") String workflowTopic
    ) {
        this.kafkaEventProducer = kafkaEventProducer;
        this.workflowTopic = workflowTopic;
    }

    /**
     * Publie un événement workflow.
     * Le correlationId DOIT déjà être présent dans l’event.
     */
    public void publish(WorkflowEventDTO event) {

        if (event.getCorrelationId() == null) {
            throw new IllegalArgumentException(
                    "correlationId must not be null when publishing workflow event"
            );
        }

        kafkaEventProducer.send(
                workflowTopic,
                event.getCorrelationId(),
                event
        );
    }
}