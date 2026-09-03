package io.multiagent.reassign.controller;

import io.multiagent.reassign.dto.AssignmentDTO;
import io.multiagent.reassign.dto.ReasoningMessageDTO;
import io.multiagent.reassign.service.ReassignAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reassign-agent")
@RequiredArgsConstructor
public class ReassignController {

    private final ReassignAgentService service;

    @PostMapping("/test")
    public AssignmentDTO test(@RequestBody ReasoningMessageDTO msg) {
        return service.processAndPublish(msg);
    }
}