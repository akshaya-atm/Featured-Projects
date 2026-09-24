package com.akshaya.elmsbackend.agent;

public record AssistantMessageRequest(
        String message,
        String conversationId
) {
}
