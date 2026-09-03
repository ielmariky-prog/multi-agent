package io.multiagent.core.config;

import io.multiagent.core.service.ExpenseIdGenerator;
import io.multiagent.core.service.WeaviateService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    @Bean
    public ExpenseIdGenerator expenseIdGenerator(WeaviateService weaviateService) {
        return new ExpenseIdGenerator(weaviateService);
    }
}
