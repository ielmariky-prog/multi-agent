package io.multiagent.core.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.multiagent.core.exception.LLMClientException;
import io.multiagent.core.model.ReRankScore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LLMAIClient {

    private final WebClient ollamaClient;
    private final String embeddingModel;
    private final String llmModel;
    private final MeterRegistry metrics;
    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;

    public LLMAIClient(
            @Value("${ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${ollama.model:llama3}") String llmModel,
            @Value("${ollama.embedding-model:nomic-embed-text}") String embeddingModel,
            @Value("${ollama.retry.max-attempts:4}") int maxAttempts,
            @Value("${ollama.retry.base-backoff-ms:1500}") long baseBackoffMs,
            @Value("${ollama.retry.max-backoff-ms:15000}") long maxBackoffMs,
            MeterRegistry registry) {

        this.ollamaClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader("content-type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.embeddingModel = embeddingModel;
        this.llmModel = llmModel;
        this.metrics = registry;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMs = Math.max(100, baseBackoffMs);
        this.maxBackoffMs = Math.max(this.baseBackoffMs, maxBackoffMs);

        log.info("LLMClient (Ollama) initialized — baseUrl={}, model={}, embedding={}", ollamaBaseUrl, llmModel, embeddingModel);
    }

    // =============================================================
    // chatJson — appelle Ollama et retourne le texte JSON brut
    // =============================================================
    public String chatJson(String model, String system, String user) {
        String targetModel = resolveModel(model, this.llmModel);

        Map<String, Object> body = Map.of(
                "model", targetModel,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)
                )
        );

        return safeCall("chat(model=" + targetModel + ")", () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = ollamaClient.post()
                    .uri("/api/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) response.get("message");
            if (message == null) {
                throw new LLMClientException("Empty response from Ollama");
            }
            String text = (String) message.get("content");
            if (text == null || text.isBlank()) {
                throw new LLMClientException("Empty content in Ollama response");
            }
            return stripMarkdown(text);
        });
    }

    // =============================================================
    // completion — utilise l'API chat d'Ollama
    // =============================================================
    public String completion(String prompt) {
        return safeCall("completion(model=" + llmModel + ")", () -> {
            Map<String, Object> body = Map.of(
                    "model", llmModel,
                    "stream", false,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = ollamaClient.post()
                    .uri("/api/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) response.get("message");
            if (message == null) return "";
            String text = (String) message.get("content");
            return text != null ? text : "";
        });
    }

    // =============================================================
    // embed — transforme un texte en vecteur float[] via Voyage AI
    // =============================================================
    public float[] embed(String text) {
        List<Float> vector = embedVector(this.embeddingModel, text);
        float[] array = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) array[i] = vector.get(i);
        return array;
    }

    // embed avec modèle spécifié, retourne List<Double>
    public List<Double> embed(String model, String text) {
        return embedVector(model, text).stream()
                .map(Float::doubleValue)
                .collect(Collectors.toList());
    }

    // =============================================================
    // rerank — trie documents par similarité cosinus (Ollama local)
    // =============================================================
    public List<ReRankScore> rerank(String query, List<String> documents) {
        if (documents == null || documents.isEmpty()) return List.of();

        return safeCall("rerank", () -> {
            float[] queryVec = embed(query);
            List<ReRankScore> scores = new ArrayList<>();

            for (int i = 0; i < documents.size(); i++) {
                float[] docVec = embed(documents.get(i));
                double score = cosineSimilarity(queryVec, docVec);
                scores.add(new ReRankScore(i, score));
            }

            scores.sort(Comparator.comparingDouble(ReRankScore::getScore).reversed());
            return scores;
        });
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // =============================================================
    // extractJSON — raccourcis : system + user → String JSON
    // =============================================================
    public String extractJSON(String prompt) {
        return chatJson(null, "Tu réponds uniquement en JSON strict sans texte supplémentaire.", prompt);
    }

    public String extractJSON(String systemPrompt, String userPrompt) {
        return chatJson(null, systemPrompt, userPrompt);
    }

    public String getLlmModel() { return llmModel; }
    public String getEmbeddingModel() { return embeddingModel; }

    // =============================================================
    // embedVector — appelle Ollama pour un texte, retourne List<Float>
    // =============================================================
    private List<Float> embedVector(String model, String text) {
        String targetModel = resolveModel(model, this.embeddingModel);

        Map<String, Object> body = Map.of(
                "model", targetModel,
                "input", text
        );

        return safeCall("embeddings.single(model=" + targetModel + ")", () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = ollamaClient.post()
                    .uri("/api/embed")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            @SuppressWarnings("unchecked")
            List<List<Number>> embeddings = (List<List<Number>>) response.get("embeddings");
            if (embeddings == null || embeddings.isEmpty()) return List.<Float>of();

            return embeddings.get(0).stream()
                    .map(n -> n.floatValue())
                    .collect(Collectors.toList());
        });
    }

    private String resolveModel(String candidate, String fallback) {
        return (candidate == null || candidate.isBlank()) ? fallback : candidate;
    }

    // Claude parfois entoure le JSON de ```json ... ``` — on nettoie avant de retourner
    private String stripMarkdown(String text) {
        if (text == null) return null;
        String t = text.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("```(?:json)?\\s*", "");
            int end = t.lastIndexOf("```");
            if (end >= 0) t = t.substring(0, end);
        }
        return t.strip();
    }

    private <T> T record(String metricSuffix, Supplier<T> supplier) {
        if (metrics == null) return supplier.get();
        Timer.Sample sample = Timer.start(metrics);
        try {
            return supplier.get();
        } finally {
            sample.stop(metrics.timer("llm." + metricSuffix));
        }
    }

    private <T> T safeCall(String operation, Supplier<T> supplier) {
        int attempt = 1;
        while (true) {
            try {
                return record(operation, supplier);
            } catch (Exception ex) {
                if (!is429(ex) || attempt >= maxAttempts) {
                    throw new LLMClientException("LLM call failed for operation=" + operation, ex);
                }
                long sleepMs = computeBackoff(attempt);
                log.warn("⏳ Rate-limited (op={} attempt {}/{}). Retry in {} ms: {}",
                        operation, attempt, maxAttempts, sleepMs, ex.getMessage());
                sleepQuietly(sleepMs);
                attempt++;
            }
        }
    }

    private boolean is429(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("429")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private long computeBackoff(int attempt) {
        double exp = Math.min(maxBackoffMs, baseBackoffMs * Math.pow(2, attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(100, 400);
        return Math.min(maxBackoffMs, (long) exp + jitter);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
