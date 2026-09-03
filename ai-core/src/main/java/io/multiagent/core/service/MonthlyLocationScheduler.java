package io.multiagent.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Envoie automatiquement une dépense de type "location" chaque début de mois
 * sur le topic intent-input-topic, afin qu'elle soit traitée par le pipeline
 * standard (classifieur + RAG).
 */
@Service
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class MonthlyLocationScheduler {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${ai-core.monthly-location.enabled:true}")
    private boolean enabled;

    @Value("${ai-core.monthly-location.topic:intent-input-topic}")
    private String topic;

    @Value("${ai-core.monthly-location.text:je loue pour la domiciliation à 450 EUR ce mois-ci}")
    private String messageText;

    /**
     * Cron : chaque 1er du mois à 08h00 UTC.
     */
    @Scheduled(cron = "0 0 8 1 * ?")
    public void sendMonthlyLocation() {
        if (!enabled) {
            return;
        }
        try {
            kafkaTemplate.send(topic, messageText);
            log.info("📤 MonthlyLocationScheduler → message envoyé sur {} : {}", topic, messageText);
        } catch (Exception e) {
            log.error("❌ MonthlyLocationScheduler échec d'envoi kafka : {}", e.getMessage(), e);
        }
    }
}
