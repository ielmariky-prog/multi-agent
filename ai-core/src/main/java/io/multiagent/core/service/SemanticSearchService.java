package io.multiagent.core.service;

import io.multiagent.core.client.LLMAIClient;
import io.multiagent.core.model.ExpenseItem;
import io.multiagent.core.util.DateRange;
import io.multiagent.core.util.DateProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service de recherche sémantique (vector search) dans Weaviate.
 * Ne fait PAS de rerank LLM, uniquement :
 *   query → embedding → nearVector → liste de textes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final LLMAIClient llm;
    private final WeaviateService weaviateService;
    private final DateProvider dateProvider;

    @Value("${ai-core.openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    /**
     * Retourne les K meilleurs chunks pour une requête donnée
     * en utilisant uniquement la similarité vectorielle.
     */
    public List<String> searchTopK(String query, int k) {
        log.info("🔍 SemanticSearchService.searchTopK(query='{}', k={})", query, k);

        // 1️⃣ Embedding de la requête
        List<Double> vector = llm.embed(embeddingModel, query);

        // 2️⃣ Recherche vectorielle dans Weaviate
        return weaviateService.searchByVector(vector, k);
    }
    public List<String> searchExpensesForPeriod(String rewrittenQuery) {
        log.info("🔎 Recherche de dépenses pour la période (rewritten) : {}", rewrittenQuery);

        DateRange range = extractDateRange(rewrittenQuery);
        if (range.getDays() > 31) {
            log.warn("⚠️ Période > 30 jours, limitation automatique appliquée.");
            range = range.limitTo30Days();
        }

        log.info("📅 Recherche entre {} et {}", range.start(), range.end());

        List<ExpenseItem> expenses = weaviateService.findExpensesBetween(range.start(), range.end());
        return expenses.stream()
                .map(this::formatExpenseDocument)
                .toList();
    }

    public DateRange extractDateRange(String text) {
        // ISO start/end présents (le prompt de rewrite doit déjà normaliser les périodes)
        Pattern p = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}).*(\\d{4}-\\d{2}-\\d{2})");
        Matcher m = p.matcher(text);
        if (m.find()) {
            LocalDate start = LocalDate.parse(m.group(1));
            LocalDate end = LocalDate.parse(m.group(2));
            return new DateRange(start, end);
        }

        // fallback : 7 derniers jours si parsing impossible
        log.warn("⚠️ Impossible d'extraire une plage de dates → fallback = 7 jours");
        LocalDate end = dateProvider.todayUtc();
        LocalDate start = end.minusDays(7);
        return new DateRange(start, end);
    }

    private String formatExpenseDocument(ExpenseItem item) {
        return """
                amount=%s %s
                type=%s
                date=%s
                description=%s
                text=%s
                """.formatted(
                item.getAmount(),
                item.getCurrency(),
                item.getType(),
                item.getDate(),
                item.getDescription(),
                item.getOriginalText());
    }
}
