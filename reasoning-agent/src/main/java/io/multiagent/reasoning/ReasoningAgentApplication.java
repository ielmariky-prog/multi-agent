package io.multiagent.reasoning;

import io.multiagent.reasoning.config.ReasoningAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Spring Boot entrypoint for the Reasoning-Agent that forwards work to AI-Core.
 */
@SpringBootApplication
@EnableKafka
@EnableConfigurationProperties(ReasoningAgentProperties.class)
public class ReasoningAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReasoningAgentApplication.class, args);
    }
}
