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

import static java.util.Objects.requireNonNull;

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
                    DeepAgent.log.info("Searching source files for: {} in directory: {}", input.query(), srcTarget);
                    
                    List<String> results = new ArrayList<>();
                    Path srcPath = Paths.get(srcTarget);
                    
                    if (!Files.exists(srcPath)) {
                        DeepAgent.log.warn("Source directory not found: {}", srcTarget);
                        return List.of("Error: Source directory not found: " + srcTarget);
                    }

                    // Normalize query - remove extension if present for better matching
                    String normalizedQuery = input.query().toLowerCase().trim();
                    final String queryWithoutExt;
                    if (normalizedQuery.endsWith(".c") || normalizedQuery.endsWith(".h") || 
                        normalizedQuery.endsWith(".cpp") || normalizedQuery.endsWith(".java")) {
                        int lastDot = normalizedQuery.lastIndexOf('.');
                        if (lastDot > 0) {
                            queryWithoutExt = normalizedQuery.substring(0, lastDot);
                        } else {
                            queryWithoutExt = normalizedQuery;
                        }
                    } else {
                        queryWithoutExt = normalizedQuery;
                    }

                    try (Stream<Path> paths = Files.walk(srcPath)) {
                        // First, try exact filename match
                        List<Path> exactMatches = paths
                                .filter(Files::isRegularFile)
                                .filter(path -> isSourceFile(path))
                                .filter(path -> {
                                    String fileName = path.getFileName().toString().toLowerCase();
                                    return fileName.equals(normalizedQuery) || 
                                           fileName.startsWith(queryWithoutExt + ".");
                                })
                                .limit(input.maxResults())
                                .collect(Collectors.toList());
                        
                        if (!exactMatches.isEmpty()) {
                            results = exactMatches.stream()
                                    .map(path -> path.toString())
                                    .collect(Collectors.toList());
                            DeepAgent.log.info("Found {} exact filename matches", results.size());
                            return results;
                        }
                        
                        // If no exact match, try content search
                        try (Stream<Path> paths2 = Files.walk(srcPath)) {
                            results = paths2
                                    .filter(Files::isRegularFile)
                                    .filter(path -> isSourceFile(path))
                                    .filter(path -> containsQuery(path, input.query()))
                                    .limit(input.maxResults())
                                    .map(path -> path.toString())
                                    .collect(Collectors.toList());
                        }
                    } catch (IOException e) {
                        DeepAgent.log.error("Error searching source files", e);
                        return List.of("Error: " + e.getMessage());
                    }

                    DeepAgent.log.info("Found {} source files matching query '{}'", results.size(), input.query());
                    if (results.isEmpty()) {
                        DeepAgent.log.warn("No files found for query '{}'. Searched in: {}", input.query(), srcTarget);
                        // Try to find similar filenames
                        try (Stream<Path> paths = Files.walk(srcPath)) {
                            List<String> similarFiles = paths
                                    .filter(Files::isRegularFile)
                                    .filter(path -> isSourceFile(path))
                                    .map(path -> path.getFileName().toString())
                                    .filter(name -> name.toLowerCase().contains(queryWithoutExt.substring(0, Math.min(3, queryWithoutExt.length()))))
                                    .limit(5)
                                    .collect(Collectors.toList());
                            
                            if (!similarFiles.isEmpty()) {
                                String suggestion = "No exact match found. Similar files: " + String.join(", ", similarFiles);
                                DeepAgent.log.info(suggestion);
                                return List.of("No files found matching '" + input.query() + "'. " + suggestion + 
                                             ". Please check if the file exists in: " + srcTarget);
                            }
                        } catch (IOException e) {
                            DeepAgent.log.debug("Error finding similar files", e);
                        }
                        return List.of("No files found matching '" + input.query() + "' in directory: " + srcTarget);
                    }
                    return results;
                })
                .inputSchema(JsonSchemaGenerator.generateForType(requireNonNull(typeRef.getType())))
                .description("Search for source code files by name or content in " + srcTarget + 
                           ". If the search doesn't return relevant results, try different keywords or search terms. " +
                           "You can call this tool multiple times with different queries to find the right files.")
                .inputType(requireNonNull(typeRef.getType()))
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
                        Path filePath;
                        
                        // If input is just a filename, search for it in src_target
                        if (!Paths.get(input.filePath()).isAbsolute() && 
                            !input.filePath().contains("/") && !input.filePath().contains("\\")) {
                            // It's just a filename, search for it
                            Path srcPath = Paths.get(srcTarget);
                            Path foundFile = findFileByName(srcPath, input.filePath());
                            
                            if (foundFile == null) {
                                return "Error: File not found: " + input.filePath() + 
                                       ". Please use search_source_files first to find the full path.";
                            }
                            filePath = foundFile;
                        } else {
                            filePath = Paths.get(input.filePath());
                        }
                        
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
                        
                        // Check if file is binary
                        if (isBinaryFile(filePath)) {
                            return "Error: File appears to be binary and cannot be read as text: " + input.filePath();
                        }
                        
                        List<String> lines;
                        try {
                            lines = Files.readAllLines(filePath, java.nio.charset.StandardCharsets.UTF_8);
                        } catch (java.nio.charset.MalformedInputException e) {
                            // Try with different encodings
                            try {
                                lines = Files.readAllLines(filePath, java.nio.charset.StandardCharsets.ISO_8859_1);
                            } catch (Exception e2) {
                                return "Error: File encoding is not supported (not UTF-8 or ISO-8859-1): " + input.filePath();
                            }
                        }
                        
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
                .inputSchema(JsonSchemaGenerator.generateForType(requireNonNull(typeRef.getType())))
                .description("Read content from a source file. Returns lines with line numbers.")
                .inputType(requireNonNull(typeRef.getType()))
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
        String lowerQuery = query.toLowerCase().trim();
        String fileName = path.getFileName().toString().toLowerCase();
        String fullPath = path.toString().toLowerCase();
        
        // Remove extension from query for better matching
        String queryWithoutExt = lowerQuery;
        if (lowerQuery.endsWith(".c") || lowerQuery.endsWith(".h") || 
            lowerQuery.endsWith(".cpp") || lowerQuery.endsWith(".java")) {
            int lastDot = lowerQuery.lastIndexOf('.');
            if (lastDot > 0) {
                queryWithoutExt = lowerQuery.substring(0, lastDot);
            }
        }
        
        // Remove extension from filename for comparison
        String fileNameWithoutExt = fileName;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileNameWithoutExt = fileName.substring(0, lastDot);
        }
        
        // First check if query matches filename exactly or partially
        if (fileName.equals(lowerQuery) || 
            fileNameWithoutExt.equals(queryWithoutExt) ||
            fileName.contains(queryWithoutExt) ||
            fileNameWithoutExt.contains(queryWithoutExt)) {
            return true;
        }
        
        // Check if query is in full path
        if (fullPath.contains(lowerQuery) || fullPath.contains(queryWithoutExt)) {
            return true;
        }
        
        try {
            // Check if file is binary or text
            if (isBinaryFile(path)) {
                // For binary files, only check filename (already checked above)
                return false;
            }
            
            // Try to read as text file with UTF-8 encoding
            String content = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            String lowerContent = content.toLowerCase();
            return lowerContent.contains(lowerQuery) || lowerContent.contains(queryWithoutExt);
        } catch (java.nio.charset.MalformedInputException e) {
            // File is not valid UTF-8, filename already checked above
            DeepAgent.log.debug("File is not valid UTF-8, filename already checked: {}", path);
            return false;
        } catch (IOException e) {
            DeepAgent.log.debug("Error reading file for search: {}", path, e);
            return false;
        }
    }
    
    /**
     * Find a file by name in the source directory
     */
    private Path findFileByName(Path searchDir, String fileName) {
        try (Stream<Path> paths = Files.walk(searchDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName) ||
                                   path.getFileName().toString().equals(fileName + ".c") ||
                                   path.getFileName().toString().equals(fileName + ".h") ||
                                   path.getFileName().toString().equals(fileName + ".cpp") ||
                                   path.getFileName().toString().equals(fileName + ".java"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            DeepAgent.log.error("Error searching for file: {}", fileName, e);
            return null;
        }
    }
    
    /**
     * Check if a file is likely binary by examining first few bytes
     */
    private boolean isBinaryFile(Path path) {
        try {
            // Read first 512 bytes to check for null bytes or binary patterns
            byte[] bytes = new byte[512];
            try (var inputStream = Files.newInputStream(path)) {
                int bytesRead = inputStream.read(bytes);
                if (bytesRead > 0) {
                    // Check for null bytes (common in binary files)
                    for (int i = 0; i < bytesRead; i++) {
                        if (bytes[i] == 0) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (IOException e) {
            DeepAgent.log.debug("Error checking if file is binary: {}", path, e);
            return false; // Assume text file if we can't determine
        }
    }
}

