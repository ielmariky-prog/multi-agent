package io.multiagent.core.controller;

import io.multiagent.core.model.IntentRequest;
import io.multiagent.core.model.IntentResult;
import io.multiagent.core.service.IntentClassifierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/intent")
@RequiredArgsConstructor
public class IntentController {

    private final IntentClassifierService classifier;

    @PostMapping("/classify")
    public IntentResult classify(@RequestBody IntentRequest request) {

        log.info("➡️ /intent/classify received ({} chars)", request.getText().length());

        // Appel du service de classification
        return classifier.classify(request.getText());
    }
}