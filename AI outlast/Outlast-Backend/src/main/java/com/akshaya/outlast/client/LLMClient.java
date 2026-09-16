package com.akshaya.outlast.client;

import com.akshaya.outlast.utils.PromptMessage;

import java.util.List;

public interface LLMClient {
    String chat(String modelName, List<PromptMessage> previousConversations);
}
