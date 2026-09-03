package io.multiagent.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Bootstraps the Audit-Agent responsible for recording every Kafka event in Weaviate.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableKafka
public class AuditAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditAgentApplication.class, args);
    }
}
