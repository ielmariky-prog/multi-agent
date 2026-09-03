
package io.multiagent.reassign;

import io.multiagent.reassign.config.ReassignAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Spring Boot entrypoint for the Reassign Agent microservice
 * that consumes reasoning outputs and publishes workflow decisions.
 */
@SpringBootApplication
@EnableKafka
@EnableConfigurationProperties(ReassignAgentProperties.class)
public class ReassignAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReassignAgentApplication.class, args);
    }
}
