package com.akshaya.shopsphere.chat;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

public class AIChatService {
    private final Assistant assistant;

    public AIChatService(Object... tools) {
        ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(System.getenv("API_KEY"))
                .modelName(System.getenv("MODEL_NAME").trim())
                .returnThinking(true)
                .sendThinking(true)
                .build();

        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                // We use a MessageWindowChatMemory to enforce a hard cap on the conversation history.
                // We increased this from 20 to 100 to prevent premature eviction. Gemini requires a strict
                // alternating sequence (User -> Model -> Tool). If a small window size evicts the first
                // half of a turn, Gemini's API will reject the history with a sequence error.
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(100))
                .build();
    }

    public String chat(String sessionId, String userName, String userMessage){
        try {
            return assistant.chat(sessionId, userName, userMessage);
        } catch (HttpException e) {
            // Groq's on-demand tier has a low tokens-per-minute cap -- a burst of chat activity
            // (or one long conversation, given the memory window + tool schemas above) can hit it.
            // Without this catch, the exception escapes all the way out of ChatServlet.doPost
            // uncaught, Tomcat returns its default HTML 500 page instead of JSON, and the frontend's
            // response.json() in handleChatBotSubmit fails to parse it -- the customer sees a raw,
            // confusing error instead of a plain "try again" message.
            System.out.println("[DEBUG AIChatService] LLM HttpException: " + e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            boolean rateLimited = msg.contains("rate_limit") || msg.contains("429");
            return rateLimited
                    ? "I'm getting a lot of requests right now -- please wait a few seconds and try again."
                    : "Sorry, I'm having trouble reaching the assistant right now. Please try again in a moment.";
        } catch (dev.langchain4j.exception.RateLimitException e) {
            System.out.println("[DEBUG AIChatService] LLM RateLimitException: " + e.getMessage());
            return "I'm getting a lot of requests right now -- please wait a few seconds and try again.";
        } catch (RuntimeException e) {
            System.out.println("[DEBUG AIChatService] Unexpected chat error: " + e);
            return "Sorry, something went wrong on my end. Please try again.";
        }
    }
}
