package io.multiagent.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AIConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}