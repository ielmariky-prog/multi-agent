package io.multiagent.core.model;

import lombok.Data;

@Data
public class RAGChunk {

    /** Identifiant logique du chunk (ex: chunk-0, id weaviate, etc.) */
    private String id;

    /** Texte du chunk */
    private String text;

    /** Source (weaviate, fichier, base, etc.) */
    private String source;
}