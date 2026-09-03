package io.multiagent.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.multiagent.core.client.LLMAIClient;
import io.multiagent.core.util.DateProvider;
import io.multiagent.core.util.LLMUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Service de réécriture de requête (Query Rewrite),
 * pour améliorer le rappel/pertinence du RAG.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService implements InitializingBean {

    private final LLMAIClient llm;
    private final DateProvider dateProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai-core.openai.rewrite-model:gpt-4o-mini}")
    private String rewriteModel;
    @Value("${AI_CORE_PROMPT_REWRITE_SYSTEM:}")
    private String rewriteSystemPromptEnv;
    private String systemPromptTemplate;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (rewriteSystemPromptEnv != null && !rewriteSystemPromptEnv.isBlank()) {
            systemPromptTemplate = rewriteSystemPromptEnv;
        } else {
            systemPromptTemplate = loadFromClasspath("prompts/prompt_rewrite_system.txt");
        }
    }

    private String loadFromClasspath(String path) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Fichier prompt introuvable : " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Réécrit une requête utilisateur en langage plus clair et ciblé.
     * Résultat mis en cache pour limiter les coûts OpenAI.
     */
    @Cacheable("queryRewrite")
    public String rewrite(String query) {
        log.info("🔁 QueryRewriteService.rewrite()");

        LocalDate today = dateProvider.todayUtc();
        String todayStr = today.toString();

        String system = systemPromptTemplate.formatted(todayStr);

        String user = """
                Voici la requête utilisateur :
                "%s"

                Donne uniquement le JSON demandé.
                """.formatted(query);

        try {
            var completion = llm.chatJson(rewriteModel, system, user);
            String content = LLMUtils.extractChatContent(completion);

            JsonNode json = mapper.readTree(content);
            String rewrite = json.path("rewrite").asText(query);

            log.debug("🔁 Rewrite: '{}' → '{}'", query, rewrite);
            return rewrite;

        } catch (Exception e) {
            log.warn("⚠️ QueryRewriteService: échec du rewrite, fallback à la requête originale. Cause: {}",
                    e.getMessage());
            return query;
        }
    }

}
