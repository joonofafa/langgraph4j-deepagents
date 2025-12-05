package org.bsc.langgraph4j.deepagents;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tools for searching and reading source code from the file system
 */
@Component
public class SourceCodeSearchTools {

    private final String srcTarget;
    private final String docTarget;

    public SourceCodeSearchTools(
            @Value("${source.src_target:src/main/java}") String srcTarget,
            @Value("${source.doc_target:docs}") String docTarget) {
        this.srcTarget = srcTarget;
        this.docTarget = docTarget;
    }

    record SearchSourceArgs(
            @JsonPropertyDescription("Search query to find relevant source files")
            @JsonProperty(required = true)
            String query,
            @JsonPropertyDescription("Maximum number of files to return")
            @JsonProperty(defaultValue = "10")
            int maxResults
    ) {}

    /**
     * Search for source files containing the query string
     */
    public ToolCallback searchSourceFiles() {
        final var typeRef = new TypeReference<SearchSourceArgs>() {};

        return FunctionToolCallback.<SearchSourceArgs, List<String>>builder(
                "search_source_files", (input, context) -> {
                    DeepAgent.log.info("Searching source files for: {}", input.query());
                    
                    List<String> results = new ArrayList<>();
                    Path srcPath = Paths.get(srcTarget);
                    
                    if (!Files.exists(srcPath)) {
                        DeepAgent.log.warn("Source directory not found: {}", srcTarget);
                        return List.of("Error: Source directory not found: " + srcTarget);
                    }

                    try (Stream<Path> paths = Files.walk(srcPath)) {
                        results = paths
                                .filter(Files::isRegularFile)
                                .filter(path -> isSourceFile(path))
                                .filter(path -> containsQuery(path, input.query()))
                                .limit(input.maxResults())
                                .map(path -> path.toString())
                                .collect(Collectors.toList());
                    } catch (IOException e) {
                        DeepAgent.log.error("Error searching source files", e);
                        return List.of("Error: " + e.getMessage());
                    }

                    DeepAgent.log.info("Found {} source files matching query", results.size());
                    return results;
                })
                .inputSchema(JsonSchemaGenerator.generateForType(typeRef.getType()))
                .description("Search for source code files containing the query string in " + srcTarget)
                .inputType(typeRef.getType())
                .build();
    }

    record ReadSourceFileArgs(
            @JsonPropertyDescription("Path to the source file to read")
            @JsonProperty(required = true)
            String filePath,
            @JsonPropertyDescription("Starting line number (1-based)")
            @JsonProperty(defaultValue = "1")
            int startLine,
            @JsonPropertyDescription("Number of lines to read")
            @JsonProperty(defaultValue = "100")
            int numLines
    ) {}

    /**
     * Read content from a source file
     */
    public ToolCallback readSourceFile() {
        final var typeRef = new TypeReference<ReadSourceFileArgs>() {};

        return FunctionToolCallback.<ReadSourceFileArgs, String>builder(
                "read_source_file", (input, context) -> {
                    DeepAgent.log.info("Reading source file: {}", input.filePath());
                    
                    try {
                        Path filePath = Paths.get(input.filePath());
                        
                        // Security check: ensure file is within src_target or doc_target
                        Path srcPath = Paths.get(srcTarget).toAbsolutePath().normalize();
                        Path docPath = Paths.get(docTarget).toAbsolutePath().normalize();
                        Path absoluteFilePath = filePath.toAbsolutePath().normalize();
                        
                        if (!absoluteFilePath.startsWith(srcPath) && !absoluteFilePath.startsWith(docPath)) {
                            return "Error: File path is outside allowed directories (src_target or doc_target)";
                        }
                        
                        if (!Files.exists(filePath)) {
                            return "Error: File not found: " + input.filePath();
                        }
                        
                        List<String> lines = Files.readAllLines(filePath);
                        
                        // Apply line range
                        int startIdx = Math.max(0, input.startLine() - 1);
                        int endIdx = Math.min(lines.size(), startIdx + input.numLines());
                        
                        if (startIdx >= endIdx) {
                            return "Error: Invalid line range";
                        }
                        
                        // Format with line numbers
                        StringBuilder result = new StringBuilder();
                        result.append("File: ").append(input.filePath()).append("\n");
                        result.append("Lines: ").append(startIdx + 1).append("-").append(endIdx).append("\n\n");
                        
                        for (int i = startIdx; i < endIdx; i++) {
                            result.append(String.format("%5d: %s%n", i + 1, lines.get(i)));
                        }
                        
                        return result.toString();
                        
                    } catch (IOException e) {
                        DeepAgent.log.error("Error reading source file", e);
                        return "Error reading file: " + e.getMessage();
                    }
                })
                .inputSchema(JsonSchemaGenerator.generateForType(typeRef.getType()))
                .description("Read content from a source file. Returns lines with line numbers.")
                .inputType(typeRef.getType())
                .build();
    }

    private boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".java") ||
               fileName.endsWith(".kt") ||
               fileName.endsWith(".scala") ||
               fileName.endsWith(".groovy") ||
               fileName.endsWith(".js") ||
               fileName.endsWith(".ts") ||
               fileName.endsWith(".py") ||
               fileName.endsWith(".cpp") ||
               fileName.endsWith(".c") ||
               fileName.endsWith(".h") ||
               fileName.endsWith(".hpp");
    }

    private boolean containsQuery(Path path, String query) {
        try {
            String content = Files.readString(path);
            String lowerQuery = query.toLowerCase();
            String lowerContent = content.toLowerCase();
            return lowerContent.contains(lowerQuery) || 
                   path.toString().toLowerCase().contains(lowerQuery);
        } catch (IOException e) {
            DeepAgent.log.debug("Error reading file for search: {}", path, e);
            return false;
        }
    }
}

