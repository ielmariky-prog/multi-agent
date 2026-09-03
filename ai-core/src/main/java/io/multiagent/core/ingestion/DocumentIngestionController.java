package io.multiagent.core.ingestion;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.multiagent.core.service.IndexingService;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentIngestionController {

    private final IndexingService indexingService;

    @PostMapping("/ingest")
    public String ingest(@RequestBody String text) {
        int count = indexingService.indexDocument(text, "rest");
        return "OK - " + count + " chunks indexés";
    }
}