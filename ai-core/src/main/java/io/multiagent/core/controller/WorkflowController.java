package io.multiagent.core.controller;

import io.multiagent.core.model.WorkflowDecision;
import io.multiagent.core.model.WorkflowDecisionRequest;
import io.multiagent.core.service.WorkflowDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entrypoint for workflow decisions requested by thin agents such as Reassign-Agent.
 * Delegates the reasoning payload to {@link WorkflowDecisionService} and returns the decision JSON.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/workflow")
public class WorkflowController {

    private final WorkflowDecisionService workflowDecisionService;

    @PostMapping("/reassign")
    public WorkflowDecision decide(@RequestBody WorkflowDecisionRequest request) {
        log.info("➡️ /workflow/reassign payload size={} bytes",
                request != null && request.reasoningPayload() != null ?
                        request.reasoningPayload().length() : 0);
        return workflowDecisionService.decide(request.reasoningPayload());
    }
}
