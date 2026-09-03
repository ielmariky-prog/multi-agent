package io.multiagent.core.service;

import io.multiagent.core.client.LLMAIClient;
import io.multiagent.core.model.ReRankResponse;
import io.multiagent.core.model.ReRankResult;
import io.multiagent.core.model.ReRankScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service de re-ranking basé sur l'API Responses de OpenAI (via LLMAIClient),
 * conforme aux bonnes pratiques 2025.
 *
 * Rôle :
 *  - Prendre une requête utilisateur + des passages candidats (documents/chunks)
 *  - Déléguer le rerank à LLMAIClient (openai-java 4.8.0)
 *  - Retourner une structure claire ReRankResponse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReRankService {

    private final LLMAIClient llm;

    /**
     * Re-rank des documents en utilisant un modèle LLM (gpt-4o-mini par exemple).
     *
     * @param query     question / requête utilisateur
     * @param documents liste de passages candidats (chunks de RAG)
     * @return          ReRankResponse contenant les documents triés par pertinence
     */
    public ReRankResponse rerank(String query, List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("ReRankService.rerank appelé avec une liste de documents vide");
            ReRankResponse empty = new ReRankResponse();
            empty.setResults(List.of());
            return empty;
        }

        log.info("🔎 ReRankService → rerank {} documents pour la requête '{}'", documents.size(), query);

        // 1. Appel LLMAIClient → OpenAI Responses API (Rerank)
        List<ReRankScore> scores = llm.rerank(query, documents);

        if (scores == null || scores.isEmpty()) {
            log.warn("ReRankService → LLMAIClient.rerank a retourné une liste vide");
            ReRankResponse empty = new ReRankResponse();
            empty.setResults(List.of());
            return empty;
        }

        // 2. Tri des scores par pertinence décroissante
        List<ReRankResult> results = scores.stream()
                .sorted(Comparator.comparingDouble(ReRankScore::getScore).reversed())
                .map(score -> {
                    int index = score.getIndex();
                    String doc = (index >= 0 && index < documents.size())
                            ? documents.get(index)
                            : "";

                    ReRankResult r = new ReRankResult();
                    r.setIndex(index);
                    r.setDocument(doc);
                    r.setRelevance(score.getScore());
                    return r;
                })
                .collect(Collectors.toList());

        ReRankResponse response = new ReRankResponse();
        response.setResults(results);

        log.info("✅ ReRankService → rerank terminé, top1 score = {}",
                results.isEmpty() ? "n/a" : results.get(0).getRelevance());

        return response;
    }

    /**
     * Méthode utilitaire : retourne directement la liste des meilleurs documents rerankés.
     *
     * @param query     requête utilisateur
     * @param documents candidats
     * @param topK      nombre de documents à garder
     * @return          liste des K meilleurs documents
     */
   public List<String> rerankAndExtractTopK(String query, List<String> documents, int topK) {

    if (documents == null || documents.isEmpty()) {
        return List.of(); // aucun chunk → éviter erreur LLM
    }

    int limit = (topK <= 0) ? documents.size() : Math.min(topK, documents.size());

    ReRankResponse response = rerank(query, documents);

    return response.getResults().stream()
            .limit(limit)
            .map(ReRankResult::getDocument)
            .toList();
}
}