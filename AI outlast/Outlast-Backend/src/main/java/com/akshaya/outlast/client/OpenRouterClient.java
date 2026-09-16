package com.akshaya.outlast.client;

import java.util.List;

import com.akshaya.outlast.utils.PromptMessage;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class OpenRouterClient implements LLMClient {
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Simulation cancelled");
        }
    }

    public final OpenAIClient openAIClient;
    public OpenRouterClient(String baseUrl, String apiKey) {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }
    @Override
    public String chat(String model, List<PromptMessage> messages) {
        checkInterrupted();
        String resolvedModel = model;
        if (model.equalsIgnoreCase("gpt-20b") || model.equalsIgnoreCase("gpt 20b") || model.equalsIgnoreCase("openai/gpt-oss-20b")) {
            resolvedModel = "openai/gpt-oss-20b";
        } else if (model.equalsIgnoreCase("gpt-120b") || model.equalsIgnoreCase("gpt 120b") || model.equalsIgnoreCase("openai/gpt-oss-120b")) {
            resolvedModel = "openai/gpt-oss-120b";
        }
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder().model(resolvedModel);
        for (PromptMessage msg : messages) {
            if (msg.role().equalsIgnoreCase("system")) {
                builder.addSystemMessage(msg.content());
            } else if (msg.role().equalsIgnoreCase("user")) {
                builder.addUserMessage(msg.content());
            } else if (msg.role().equalsIgnoreCase("assistant")) {
                builder.addAssistantMessage(msg.content());
            } else {
                throw new IllegalArgumentException("Unknown role: " + msg.role());
            }
        }
        builder.temperature(0.9)
                .maxCompletionTokens(4096);
        ChatCompletionCreateParams params = builder.build();

        // Introduce a short pacing delay (1.5s) to avoid hitting the 15 RPM free tier limit
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CancellationException("Simulation cancelled");
        }

        int maxRetries = 6;
        int delayMs = 4000; // Start with 4s delay if a rate limit is hit

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            checkInterrupted();
            try {
                ChatCompletion completion = openAIClient.chat().completions().create(params);
                checkInterrupted();
                return completion.choices().get(0).message().content().orElse("I have nothing more to say");
            } catch (Exception e) {
                checkInterrupted();
                if (attempt == maxRetries) {
                    System.err.println("[LLM FATAL ERROR] All retry attempts failed.");
                    throw e;
                }
                System.out.println("\n[LLM RATE LIMIT / RETRY] Request failed: " + e.getMessage());
                System.out.println("[LLM RATE LIMIT / RETRY] Retrying (attempt " + attempt + "/" + (maxRetries - 1) + ") in " + (delayMs / 1000) + " seconds...");
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                delayMs *= 2; // Exponential backoff (4s -> 8s -> 16s -> 32s -> 64s)
            }
        }

        return "I have nothing more to say";
    }
}
