package org.bsc.langgraph4j.deepagents;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ChatModelConfiguration {

    @Bean
    @Profile("ollama")
    public ChatModel ollamaModel() {
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
                .defaultOptions(OllamaOptions.builder()
                        .model("qwen2.5:7b")
                        .temperature(0.1)
                        .build())
                .build();
    }

    @Bean
    @Profile("openai")
    public ChatModel openaiModel(
            @Value("${spring.ai.openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${spring.ai.openai.base-url:#{null}}") String baseUrl) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                "OpenAI API key is required when using 'openai' profile. " +
                "Please set OPENAI_API_KEY environment variable or configure spring.ai.openai.api-key in application.yaml"
            );
        }
        
        var apiBuilder = OpenAiApi.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            apiBuilder.baseUrl(baseUrl);
        }
        
        return OpenAiChatModel.builder()
                .openAiApi(apiBuilder.build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .logprobs(false)
                        .temperature(0.1)
                        .build())
                .build();

    }

    @Bean
    @Profile("gemini")
    public ChatModel geminiModel() {
        return VertexAiGeminiChatModel.builder()
                .vertexAI( new VertexAI.Builder()
                        .setProjectId(System.getenv("GOOGLE_CLOUD_PROJECT"))
                        .setLocation(System.getenv("GOOGLE_CLOUD_LOCATION"))
                        .setTransport(Transport.REST)
                        .build())
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model("gemini-2.5-pro")
                        .temperature(0.0)
                        .build())
                .build();

    }

    @Bean
    @Profile("github-models")
    public ChatModel githubModel() {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl("https://models.github.ai/inference") // GITHUB MODELS
                        .apiKey(System.getenv("GITHUB_MODELS_TOKEN"))
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .logprobs(false)
                        .temperature(0.1)
                        .build())
                .build();

    }

}
