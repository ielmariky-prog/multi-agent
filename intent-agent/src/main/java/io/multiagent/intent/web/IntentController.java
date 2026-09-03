package io.multiagent.intent.controller;

import io.multiagent.intent.dto.IntentDTO;
import io.multiagent.intent.service.IntentAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/intent-agent")
@RequiredArgsConstructor
public class IntentController {

    private final IntentAgentService service;

    @PostMapping("/classify")
    public IntentDTO classify(@RequestBody String text) {
        return service.classifyAndPublish(text, null);
    }
}