package io.multiagent.core.controller;

import io.multiagent.core.model.AssignmentResult;
import io.multiagent.core.model.ReasoningResult;
import io.multiagent.core.service.ReassignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/reassign")
public class ReassignController {

    private final ReassignService reassignService;

    /**
     * Endpoint appelé par reassign-agent.
     * Le corps attendu est un ReasoningResult (JSON),
     * identique à ce que renvoie /reasoning/analyze.
     */
    @PostMapping("/assign")
    public AssignmentResult assign(@RequestBody ReasoningResult rr) {
        log.info("📨 [AI-Core] /reassign/assign reçu – status={}, confidence={}",
                rr.getStatus(), rr.getConfidence());
        return reassignService.assign(rr);
    }
}