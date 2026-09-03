package io.multiagent.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service d’ingestion / indexation :
 *  - découpe le texte en chunks
 *  - délègue à WeaviateService pour embedding + stockage vectoriel
 */
@Slf4j
@Service
public class IndexingService {

    private final ChunkingService chunkingService;
    private final WeaviateService weaviateService;
    private final long chunkSleepMs;

    public IndexingService(ChunkingService chunkingService,
                           WeaviateService weaviateService,
                           @Value("${ai-core.ingestion.chunk-sleep-ms:0}") long chunkSleepMs) {
        this.chunkingService = chunkingService;
        this.weaviateService = weaviateService;
        this.chunkSleepMs = Math.max(0, chunkSleepMs);
    }

    /**
     * Indexe un document complet dans Weaviate.
     *
     * @param rawText Texte brut (email, PDF OCR, note de frais, etc.)
     * @param source  Origine (ex: "kafka", "rest", "upload_pdf", "note_frais")
     * @return nombre de chunks réellement indexés
     */
    public int indexDocument(String rawText, String source) {
        if (rawText == null || rawText.isBlank()) {
            log.warn("❗ indexDocument appelé avec un texte vide");
            return 0;
        }

        // 1) Découpage en chunks
        List<String> chunks = chunkingService.chunk(rawText);
        log.info("✂️ Document découpé en {} chunks (source={})", chunks.size(), source);

        int success = 0;

        // 2) Pour chaque chunk → embedding + stockage dans Weaviate
        for (String chunk : chunks) {
            try {
                weaviateService.indexChunk(chunk, source);
                success++;
            } catch (Exception e) {
                log.error("❌ Erreur lors de l’indexation d’un chunk ({} chars)", chunk.length(), e);
            } finally {
                maybeThrottle();
            }
        }

        log.info("📦 Indexation terminée : {}/{} chunks indexés (source={})",
                success, chunks.size(), source);

        return success;
    }

    /**
     * Variante pratique : source par défaut.
     */
    public int indexDocument(String rawText) {
        return indexDocument(rawText, "manual");
    }

    private void maybeThrottle() {
        if (chunkSleepMs > 0) {
            try {
                Thread.sleep(chunkSleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
