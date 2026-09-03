package io.multiagent.core.service;

import org.springframework.stereotype.Service;
import java.util.*;
/**
 * Scinde un texte long en morceaux (« chunks ») de taille raisonnable (env. 800 caractères). 
 * C’est nécessaire pour indexer des documents dans Weaviate sans dépasser la limite d’input d’OpenAI et pour améliorer 
 * la qualité des embeddings.

 */
@Service
public class ChunkingService {

    private static final int MAX_CHUNK = 800;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();

        // Split par paragraphes
        String[] paras = text.split("\\n\\n+");

        for (String p : paras) {
            p = p.trim();
            if (p.isEmpty()) continue;

            // Si paragraphe trop long → recoupe
            if (p.length() <= MAX_CHUNK) {
                chunks.add(p);
            } else {
                chunks.addAll(splitLongParagraph(p));
            }
        }
        return chunks;
    }

    // Découpe propre pour les grands paragraphes
    private List<String> splitLongParagraph(String paragraph) {
        List<String> parts = new ArrayList<>();

        String[] words = paragraph.split(" ");
        StringBuilder current = new StringBuilder();

        for (String w : words) {
            if (current.length() + w.length() + 1 > MAX_CHUNK) {
                parts.add(current.toString());
                current = new StringBuilder();
            }
            current.append(w).append(" ");
        }

        if (current.length() > 0) parts.add(current.toString());

        return parts;
    }
}