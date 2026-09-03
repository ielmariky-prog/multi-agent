package io.multiagent.core.ingestion;

import io.multiagent.core.service.IndexingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class IngestionKafkaListener {

    private final IndexingService indexingService;

    @KafkaListener(
            topics = "${ai-core.ingestion.topic:documents.raw}",
            groupId = "${spring.kafka.consumer.group-id:ai-core-ingestion}")
    public void onNewDocument(String rawText) {
        int count = indexingService.indexDocument(rawText, "kafka");
        log.info("✅ Ingestion Kafka terminée, {} chunks indexés", count);
    }
}
