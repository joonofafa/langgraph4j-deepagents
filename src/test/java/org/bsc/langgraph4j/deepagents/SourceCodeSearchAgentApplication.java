package org.bsc.langgraph4j.deepagents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Main application for Source Code Search Agent
 * 
 * This application allows users to ask questions about source code.
 * The agent will search source files and provide answers based on the actual code.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SourceCodeSearchAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SourceCodeSearchAgentApplication.class, args);
    }
}

