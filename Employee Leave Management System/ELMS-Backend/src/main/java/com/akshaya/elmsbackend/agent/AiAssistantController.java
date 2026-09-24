package com.akshaya.elmsbackend.agent;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/assistant")
public class AiAssistantController {

    private final AiAssistantService assistantService;

    public AiAssistantController(AiAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/messages")
    public AssistantMessageResponse sendMessage(
            @RequestBody AssistantMessageRequest request,
            Principal principal) {

        return assistantService.processMessage(
                principal.getName(),
                request.message(),
                request.conversationId()
        );
    }
}
