package org.bsc.langgraph4j.deepagents;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Example: How to use embedding tools with DeepAgent
 * 
 * NOTE: This is a template. You need to:
 * 1. Configure EmbeddingClient bean
 * 2. Uncomment the embedding tools code in Tools.java
 * 3. Add the tools to your agent
 */
@Configuration
public class EmbeddingToolsExample {

    /**
     * Example: Create an agent with embedding tools
     * 
     * Uncomment and modify when EmbeddingClient is available:
     */
    /*
    @Bean
    public List<ToolCallback> embeddingTools(EmbeddingClient embeddingClient) {
        return Tools.embeddingTools(embeddingClient);
    }

    public void exampleUsage(ChatModel chatModel, EmbeddingClient embeddingClient) {
        // Get embedding tools
        List<ToolCallback> embeddingTools = Tools.embeddingTools(embeddingClient);
        
        // Create agent with embedding tools
        var agent = DeepAgent.builder()
                .chatModel(chatModel)
                .tools(embeddingTools)
                .instructions("""
                    You can use embed_documents to embed all documents in a directory,
                    and search_embeddings to search them semantically.
                    """)
                .build();
        
        // Use the agent...
    }
    */
}

