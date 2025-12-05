package org.bsc.langgraph4j.deepagents;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Test agent for searching source code and answering questions based on found code
 * 
 * This agent:
 * 1. Takes user questions
 * 2. Searches source code in src_target directory
 * 3. Reads relevant source files
 * 4. Uses LLM to generate answers based on the found code
 */
@Controller
@org.springframework.context.annotation.Profile("!deepagents")
public class SourceCodeSearchAgentController implements CommandLineRunner {
    
    private static final org.slf4j.Logger log = DeepAgent.log;
    
    private final ChatModel chatModel;
    private final SourceCodeSearchTools sourceTools;

    public SourceCodeSearchAgentController(ChatModel chatModel, SourceCodeSearchTools sourceTools) {
        this.chatModel = chatModel;
        this.sourceTools = sourceTools;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("================================================================");
        log.info("Source Code Search Agent - Test Program");
        log.info("================================================================");
        log.info("This agent searches source code and answers questions based on found code.");
        log.info("Type 'exit' or 'quit' to stop.");
        log.info("================================================================");
        
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("\n질문을 입력하세요 (종료: 'exit' 또는 'quit'): ");
            String question = scanner.nextLine().trim();
            
            if (question.isEmpty()) {
                continue;
            }
            
            if (question.equalsIgnoreCase("exit") || question.equalsIgnoreCase("quit")) {
                System.out.println("프로그램을 종료합니다.");
                break;
            }
            
            try {
                processQuestion(question);
            } catch (Exception e) {
                log.error("Error processing question", e);
                System.err.println("오류가 발생했습니다: " + e.getMessage());
            }
        }
        
        scanner.close();
    }

    private void processQuestion(String question) throws Exception {
        System.out.println("\n================================================================");
        System.out.println("질문 처리 중: " + question);
        System.out.println("================================================================");
        
        // Create agent with source code search tools
        var agent = DeepAgent.builder()
                .instructions("""
                You are a helpful code assistant. Your job is to answer questions about source code.
                
                When a user asks a question:
                1. First, use the search_source_files tool to find relevant source files
                2. Then, use the read_source_file tool to read the content of relevant files
                3. Analyze the code and provide a detailed answer based on the actual source code
                
                Important:
                - Always search for source files first before reading
                - Read multiple files if needed to get complete context
                - Base your answer on the actual code you read, not on assumptions
                - If you cannot find relevant code, say so clearly
                - Provide specific file paths and line numbers when referencing code
                
                Answer in Korean if the question is in Korean, otherwise answer in English.
                """)
                .chatModel(chatModel)
                .tools(List.of(
                    sourceTools.searchSourceFiles(),
                    sourceTools.readSourceFile()
                ))
                .build()
                .compile(CompileConfig.builder()
                        .recursionLimit(50)
                        .build());

        Map<String, Object> input = Map.of("messages", new UserMessage(question));
        var runnableConfig = RunnableConfig.builder().build();

        System.out.println("\n[1/3] 소스 코드 검색 중...");
        var result = agent.stream(input, runnableConfig);

        System.out.println("[2/3] 관련 파일 분석 중...");
        var output = result.stream()
                .peek(s -> {
                    if (s.node() != null) {
                        System.out.println("  → " + s.node());
                    }
                })
                .reduce((a, b) -> b)
                .orElseThrow();

        System.out.println("[3/3] 답변 생성 완료\n");
        
        // Print results
        printResults(output);
    }

    private void printResults(Object outputObj) {
        try {
            var stateMethod = outputObj.getClass().getMethod("state");
            var state = (DeepAgent.State) stateMethod.invoke(outputObj);
            
            System.out.println("================================================================");
            System.out.println("참고된 파일들");
            System.out.println("================================================================");
            if (state.files().isEmpty()) {
                System.out.println("(참고된 파일 없음)");
            } else {
                state.files().keySet().forEach(file -> 
                    System.out.println("  - " + file)
                );
            }
            
            System.out.println("\n================================================================");
            System.out.println("답변");
            System.out.println("================================================================");
            
            var lastMessage = state.lastMessage()
                    .map(AssistantMessage.class::cast)
                    .map(AssistantMessage::getText)
                    .orElse("답변을 생성할 수 없습니다.");
            
            System.out.println(lastMessage);
            System.out.println("================================================================");
            
        } catch (Exception e) {
            log.error("Failed to print results", e);
            System.err.println("결과 출력 중 오류: " + e.getMessage());
        }
    }
}

