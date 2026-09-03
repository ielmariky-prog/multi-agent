
package io.multiagent.intent;

import io.multiagent.intent.config.IntentAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Bootstraps the Intent-Agent responsible for classifying inbound events via AI-Core.
 */
@SpringBootApplication
@EnableKafka
@EnableConfigurationProperties(IntentAgentProperties.class)
public class IntentAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntentAgentApplication.class, args);
    }
}
