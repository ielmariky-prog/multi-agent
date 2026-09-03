package io.multiagent.core.controller;

import io.multiagent.core.client.LLMAIClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final LLMAIClient openAIClient;

    @PostMapping
    public Map<String, Object> embed(@RequestBody Map<String, String> body) {
        String text = body.get("text");

        float[] embedding = openAIClient.embed(text);
       //List<Float> vctor = llm.embedding(text);
        List<Float> vector = new ArrayList<Float>();
        for (int i = 0; i < embedding.length; i++) {
            vector.add(embedding[i]);
        }

        return Map.of(
            "text", text,
            "embedding", vector,
            "size", vector.size()
        );
    }
}