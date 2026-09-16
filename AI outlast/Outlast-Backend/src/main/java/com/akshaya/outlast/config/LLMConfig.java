package com.akshaya.outlast.config;

import com.akshaya.outlast.client.LLMClient;
import com.akshaya.outlast.client.OpenRouterClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMConfig {

    @Value("${groq.base-url}")
    private String groqUrl;

    @Value("${groq.api-key}")
    private String groqKey;

    @Value("${ollama.base-url}")
    private String ollamaUrl;

    @Value("${ollama.api-key}")
    private String ollamaKey;

    @Value("${gemini.base-url}")
    private String geminiUrl;

    @Value("${gemini.api-key}")
    private String geminiKey;

    @Value("${llm.provider:groq}")
    private String provider;

    @Bean
    public LLMClient openRouterClient() {
        return switch (provider.toLowerCase()) {
            case "ollama" -> {
                System.out.println("[LLM] Provider: Ollama → " + ollamaUrl);
                yield new OpenRouterClient(ollamaUrl, ollamaKey);
            }
            case "gemini" -> {
                System.out.println("[LLM] Provider: Gemini → " + geminiUrl);
                yield new OpenRouterClient(geminiUrl, geminiKey);
            }
            default -> {
                System.out.println("[LLM] Provider: Groq → " + groqUrl);
                yield new OpenRouterClient(groqUrl, groqKey);
            }
        };
    }
}