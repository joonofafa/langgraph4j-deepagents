package org.bsc.langgraph4j.deepagents;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.spring.ai.tool.SpringAIToolResponseBuilder;
// Note: Embedding and Vector Store imports - requires Spring AI dependencies
// Uncomment when Spring AI embedding dependencies are available
// import org.springframework.ai.document.Document;
// import org.springframework.ai.embedding.EmbeddingClient;
// import org.springframework.ai.transformer.splitter.TextSplitter;
// import org.springframework.ai.transformer.splitter.TokenTextSplitter;
// import org.springframework.ai.vectorstore.SimpleVectorStore;
// import org.springframework.ai.vectorstore.VectorStore;
// import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

// Embedding tools imports (uncomment when using embedding functionality)
// import java.io.IOException;
// import java.io.FileInputStream;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.stream.Collectors;
// import java.util.stream.Stream;
// 
// Apache POI imports for Microsoft Office documents (uncomment when using)
// import org.apache.poi.xwpf.usermodel.XWPFDocument;
// import org.apache.poi.xwpf.usermodel.XWPFParagraph;
// import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// import org.apache.poi.xssf.usermodel.XSSFSheet;
// import org.apache.poi.xssf.usermodel.XSSFRow;
// import org.apache.poi.xssf.usermodel.XSSFCell;
// import org.apache.poi.xslf.usermodel.XMLSlideShow;
// import org.apache.poi.xslf.usermodel.XSLFSlide;
// import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.bsc.langgraph4j.deepagents.Prompts.EDIT_DESCRIPTION;

interface Tools {

    static ToolCallback ls() {
        return  FunctionToolCallback.<Void, Collection<String>>builder( "ls", ( noArgs, context ) -> {
            var state = new DeepAgent.State(context.getContext());

            var result = state.files().keySet();

            DeepAgent.log.debug( "tool: 'ls' call: {}", result );

            return result;
        })
        .description("List all files in the mock filesystem")
        .inputType( Void.class )
        .build();
    }

    record writeTodosArgs(
            @JsonProperty(required = true)
            @JsonPropertyDescription("todo list to update")
            List<DeepAgent.ToDo> toDos
    ) {}

    static ToolCallback writeTodos() {

        final var typeRef = new TypeReference<writeTodosArgs>() {};
        final var mapper = new ObjectMapper();

        return FunctionToolCallback.<writeTodosArgs, String>builder( "write_todos", (input, context ) -> {
            try {
                DeepAgent.log.debug( "tool: 'writeTodos' call: {}", input);

                return SpringAIToolResponseBuilder.of(context)
                        .update(Map.of("todos", input.toDos()))
                        .buildAndReturn( format("Updated todo list to %s", mapper.writeValueAsString(input)) );

            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        })
        .inputSchema( JsonSchemaGenerator.generateForType(requireNonNull(typeRef.getType())) )
        .inputType(requireNonNull(typeRef.getType()))
        .description(Prompts.WRITE_TODOS_DESCRIPTION)
        .build();

    }


    record ReadFileArgs(
            @JsonProperty(required = true)
            String filePath,
            @JsonProperty(defaultValue="0")
            int offset,
            @JsonProperty(required=true, defaultValue="2000")
            int limit) {}

    static ToolCallback  readFile() {

        final var typeRef = new TypeReference<ReadFileArgs>() {};

        return FunctionToolCallback.<ReadFileArgs, String>builder( "read_file", ( input, context ) -> {
                    DeepAgent.log.debug( "tool: 'read_file' call: {}", input);

                    final var state = new DeepAgent.State(context.getContext());

                    final var mockFilesystem = state.files();

                    if( !mockFilesystem.containsKey( input.filePath() ) ) {
                        return format("Error: File '%s' not found", input.filePath());
                    }

                    // Get file content
                    final var content = mockFilesystem.get(input.filePath());

                    // Handle empty file
                    if (content.isEmpty()) {
                        return "System reminder: File exists but has empty contents";
                    }

                    DeepAgent.log.debug( "tool: 'read_file' {}\n{}", input.filePath(), content);

                    // Split content into lines
                    final var lines = content.split("\n");

                    // Apply line offset and limit
                    final int startIdx = input.offset();
                    final int endIdx = Math.min( startIdx + input.limit(), lines.length);

                    // Handle empty file
                    if (startIdx >= endIdx) {
                        return format("Error: illegal range error [%d,%d] reading file '%s'", startIdx, endIdx, input.filePath());
                    }

                    // Handle case where offset is beyond file length
                    if (startIdx >= lines.length) {
                        return format("Error: Line offset %d exceeds file length %d lines)",
                                input.offset(), lines.length);
                    }

                    // Format output with line numbers (cat -n format)
                    final var resultLines = new ArrayList<String>();

                    for (int i = startIdx; i < endIdx; i++) {
                        var lineContent = lines[i];
                        // Truncate lines longer than 2000 characters
                        if (lineContent.length() > 2000) {
                            lineContent = lineContent.substring(0, 2000);
                        }
                        // Line numbers start at 1, so add 1 to the index
                        resultLines.add( format("%6d\t%s", i + 1, lineContent));
                    }

                    return String.join("\n", resultLines);
                })
                .inputSchema( JsonSchemaGenerator.generateForType(requireNonNull(typeRef.getType())) )
                .description(Prompts.TOOL_DESCRIPTION)
                .inputType(requireNonNull(typeRef.getType()))
                .build();
    }

    record WriteFileArgs(
            @JsonProperty(required = true)
            String filePath,
            @JsonProperty(required = true)
            String  content) {}

    static ToolCallback  writeFile() {
        final var typeRef = new TypeReference<WriteFileArgs>() {};

        return FunctionToolCallback.<WriteFileArgs, String>builder( "write_file", ( input, context ) -> {
                DeepAgent.log.debug( "tool: 'write_file' call: {}", input);

                return SpringAIToolResponseBuilder.of( context )
                            .update( Map.of( "files", Map.of( input.filePath(), input.content() )))
                            .buildAndReturn( format("Updated file %s", input.filePath()) );
        })
        .inputSchema( JsonSchemaGenerator.generateForType(requireNonNull(typeRef.getType())) )
        .description("Write content to a file in the mock filesystem")
        .inputType(requireNonNull(typeRef.getType()))
        .build();

    }

    record EditFileArgs(
            @JsonProperty(required = true)
            String filePath,
            @JsonProperty(required = true)
            String  oldString,
            @JsonProperty(required = true)
            String  newString,
            boolean replaceAll
    ) {}

    static ToolCallback  editFile() {
        final var typeRef = new TypeReference<EditFileArgs>() {};

        return FunctionToolCallback.<EditFileArgs, String>builder( "edit_file", ( input, context ) -> {
                    DeepAgent.log.debug( "tool: 'edit_file' call: {}", input);

                    final var state = new DeepAgent.State(context.getContext());

                    final var mockFilesystem = state.files();

                    if( !mockFilesystem.containsKey( input.filePath() ) ) {
                        return format("Error: File '%s' not found", input.filePath());
                    }

                    // Get file content
                    final var content = mockFilesystem.get(input.filePath());

                    // Check if old_string exists in the file
                    if (!content.contains( input.oldString())) {
                        return format("Error: String not found in file: '%s'", input.oldString());
                    }

                    final var escapedOldString = Pattern.quote(input.oldString());

                    if (!input.replaceAll()) {
                        // Escape regex special characters

                        // Count occurrences
                        var pattern = Pattern.compile(escapedOldString);
                        var matcher = pattern.matcher(content);

                        int occurrences = 0;
                        while (matcher.find()) {
                            occurrences++;
                        }

                        // Construct message based on occurrences
                        if (occurrences > 1) {
                            return String.format(
                                    "Error: String '%s' appears %d times in file. Use replace_all=True to replace all instances, or provide a more specific string with surrounding context.",
                                    input.oldString(), occurrences
                            );
                        } else if (occurrences == 0) {
                            return String.format("Error: String not found in file: '%s'", input.oldString());
                        }
                    }

                    var newContent = (input.replaceAll() ) ?
                        content.replaceAll( escapedOldString, input.newString()) :
                        content.replaceFirst( escapedOldString, input.newString());


                    return SpringAIToolResponseBuilder.of(context)
                            .update(Map.of("files", Map.of(input.filePath(), newContent)))
                            .buildAndReturn( format("`Updated file %s", input.filePath()) );
                })
                .inputSchema( JsonSchemaGenerator.generateForType(requireNonNull(typeRef.getType())) )
                .inputType(requireNonNull(typeRef.getType()))
                .description(EDIT_DESCRIPTION)
                .build();
    }

    // Vector store cache: directory path -> vector store
    // Note: This requires Spring AI vector store implementation
    // private static final Map<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    /**
     * Create embedding tools that require EmbeddingClient
     * These tools need to be created with an EmbeddingClient instance
     * 
     * NOTE: This implementation requires Spring AI embedding and vector store dependencies.
     * Add the following to pom.xml:
     *   <dependency>
     *     <groupId>org.springframework.ai</groupId>
     *     <artifactId>spring-ai-openai</artifactId> <!-- or spring-ai-ollama, etc. -->
     *   </dependency>
     * 
     * The EmbeddingClient will be automatically available from ChatModel configuration.
     */
    static class EmbeddingTools {
        
        // Note: Uncomment when EmbeddingClient is available
        // private final EmbeddingClient embeddingClient;
        
        EmbeddingTools(Object embeddingClient) {
            // this.embeddingClient = requireNonNull((EmbeddingClient) embeddingClient, "EmbeddingClient cannot be null");
        }

        /**
         * Read text content from Microsoft Office documents
         * Supports: .docx, .xlsx, .pptx
         * Uncomment when Apache POI is available
         */
        /*
        private String readOfficeDocument(Path filePath) throws IOException {
            String fileName = filePath.getFileName().toString().toLowerCase();
            
            if (fileName.endsWith(".docx")) {
                return readDocx(filePath);
            } else if (fileName.endsWith(".xlsx")) {
                return readXlsx(filePath);
            } else if (fileName.endsWith(".pptx")) {
                return readPptx(filePath);
            }
            return "";
        }

        private String readDocx(Path filePath) throws IOException {
            StringBuilder text = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(filePath.toFile());
                 XWPFDocument document = new XWPFDocument(fis)) {
                
                for (XWPFParagraph para : document.getParagraphs()) {
                    String paraText = para.getText();
                    if (paraText != null && !paraText.trim().isEmpty()) {
                        text.append(paraText).append("\n");
                    }
                }
            }
            return text.toString();
        }

        private String readXlsx(Path filePath) throws IOException {
            StringBuilder text = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(filePath.toFile());
                 XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
                
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    XSSFSheet sheet = workbook.getSheetAt(i);
                    text.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                    
                    for (XSSFRow row : sheet) {
                        StringBuilder rowText = new StringBuilder();
                        for (XSSFCell cell : row) {
                            if (cell != null) {
                                switch (cell.getCellType()) {
                                    case STRING:
                                        rowText.append(cell.getStringCellValue()).append("\t");
                                        break;
                                    case NUMERIC:
                                        rowText.append(cell.getNumericCellValue()).append("\t");
                                        break;
                                    case BOOLEAN:
                                        rowText.append(cell.getBooleanCellValue()).append("\t");
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        if (rowText.length() > 0) {
                            text.append(rowText.toString().trim()).append("\n");
                        }
                    }
                    text.append("\n");
                }
            }
            return text.toString();
        }

        private String readPptx(Path filePath) throws IOException {
            StringBuilder text = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(filePath.toFile());
                 XMLSlideShow ppt = new XMLSlideShow(fis)) {
                
                for (XSLFSlide slide : ppt.getSlides()) {
                    text.append("Slide ").append(slide.getSlideNumber()).append(":\n");
                    for (XSLFTextShape shape : slide.getShapes()) {
                        if (shape instanceof XSLFTextShape) {
                            String shapeText = ((XSLFTextShape) shape).getText();
                            if (shapeText != null && !shapeText.trim().isEmpty()) {
                                text.append(shapeText).append("\n");
                            }
                        }
                    }
                    text.append("\n");
                }
            }
            return text.toString();
        }

        private String getFileType(String fileName) {
            if (fileName.endsWith(".docx")) return "docx";
            if (fileName.endsWith(".xlsx")) return "xlsx";
            if (fileName.endsWith(".pptx")) return "pptx";
            if (fileName.endsWith(".txt")) return "txt";
            if (fileName.endsWith(".md")) return "markdown";
            return "text";
        }
        */

        record EmbedDocumentsArgs(
                @JsonProperty(required = true)
                @JsonPropertyDescription("Directory path containing documents to embed")
                String directoryPath,
                @JsonProperty(defaultValue = "1000")
                @JsonPropertyDescription("Chunk size for text splitting")
                int chunkSize,
                @JsonProperty(defaultValue = "200")
                @JsonPropertyDescription("Chunk overlap for text splitting")
                int chunkOverlap
        ) {}

        ToolCallback embedDocuments() {
            final var typeRef = new TypeReference<EmbedDocumentsArgs>() {};

            return FunctionToolCallback.<EmbedDocumentsArgs, String>builder(
                    "embed_documents", (input, context) -> {
                        return "Error: Embedding functionality requires Spring AI embedding dependencies. " +
                               "Please add spring-ai-openai (or similar) dependency and ensure EmbeddingClient bean is configured.";
                        
                        /* Uncomment when EmbeddingClient is available:
                        DeepAgent.log.info("Embedding documents in directory: {}", input.directoryPath());

                        try {
                            Path dirPath = Paths.get(input.directoryPath());
                            
                            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                                return format("Error: Directory '%s' does not exist or is not a directory", input.directoryPath());
                            }

                            // Get or create vector store for this directory
                            VectorStore vectorStore = vectorStoreCache.computeIfAbsent(
                                    input.directoryPath(),
                                    path -> new SimpleVectorStore(embeddingClient)
                            );

                            // Find all text files and Office documents in directory
                            List<Path> textFiles;
                            try (Stream<Path> paths = Files.walk(dirPath)) {
                                textFiles = paths
                                        .filter(Files::isRegularFile)
                                        .filter(path -> {
                                            String fileName = path.getFileName().toString().toLowerCase();
                                            return fileName.endsWith(".txt") ||
                                                   fileName.endsWith(".md") ||
                                                   fileName.endsWith(".java") ||
                                                   fileName.endsWith(".py") ||
                                                   fileName.endsWith(".js") ||
                                                   fileName.endsWith(".ts") ||
                                                   fileName.endsWith(".json") ||
                                                   fileName.endsWith(".yaml") ||
                                                   fileName.endsWith(".yml") ||
                                                   fileName.endsWith(".xml") ||
                                                   fileName.endsWith(".docx") ||
                                                   fileName.endsWith(".xlsx") ||
                                                   fileName.endsWith(".pptx");
                                        })
                                        .collect(Collectors.toList());
                            }

                            if (textFiles.isEmpty()) {
                                return format("No text files found in directory: %s", input.directoryPath());
                            }

                            // Process each file
                            int totalDocuments = 0;
                            TextSplitter textSplitter = new TokenTextSplitter(input.chunkSize(), input.chunkOverlap());

                            for (Path filePath : textFiles) {
                                try {
                                    String content;
                                    String fileName = filePath.getFileName().toString().toLowerCase();
                                    
                                    // Read content based on file type
                                    if (fileName.endsWith(".docx") || fileName.endsWith(".xlsx") || fileName.endsWith(".pptx")) {
                                        // Read Office documents using Apache POI
                                        content = readOfficeDocument(filePath);
                                        if (content == null || content.trim().isEmpty()) {
                                            DeepAgent.log.warn("Failed to extract content from Office document: {}", filePath);
                                            continue;
                                        }
                                    } else {
                                        // Read text files
                                        content = Files.readString(filePath);
                                    }
                                    
                                    // Create document with metadata
                                    Document document = new Document(
                                            content,
                                            Map.of(
                                                    "filePath", filePath.toString(),
                                                    "fileName", filePath.getFileName().toString(),
                                                    "directory", input.directoryPath(),
                                                    "fileType", getFileType(fileName)
                                            )
                                    );

                                    // Split document into chunks
                                    List<Document> chunks = textSplitter.apply(List.of(document));
                                    
                                    // Add chunks to vector store
                                    vectorStore.add(chunks);
                                    totalDocuments += chunks.size();

                                    DeepAgent.log.debug("Embedded file: {} ({} chunks)", filePath, chunks.size());
                                } catch (IOException e) {
                                    DeepAgent.log.warn("Failed to read file: {}", filePath, e);
                                }
                            }

                            return format("Successfully embedded %d documents from %d files in directory: %s", 
                                    totalDocuments, textFiles.size(), input.directoryPath());

                        } catch (Exception e) {
                            DeepAgent.log.error("Error embedding documents", e);
                            return format("Error embedding documents: %s", e.getMessage());
                        }

                                    // Split document into chunks
                                    List<Document> chunks = textSplitter.apply(List.of(document));
                                    
                                    // Add chunks to vector store
                                    vectorStore.add(chunks);
                                    totalDocuments += chunks.size();

                                    DeepAgent.log.debug("Embedded file: {} ({} chunks)", filePath, chunks.size());
                                } catch (IOException e) {
                                    DeepAgent.log.warn("Failed to read file: {}", filePath, e);
                                }
                            }

                            return format("Successfully embedded %d documents from %d files in directory: %s", 
                                    totalDocuments, textFiles.size(), input.directoryPath());

                        } catch (Exception e) {
                            DeepAgent.log.error("Error embedding documents", e);
                            return format("Error embedding documents: %s", e.getMessage());
        }

        private String getFileType(String fileName) {
            if (fileName.endsWith(".docx")) return "docx";
            if (fileName.endsWith(".xlsx")) return "xlsx";
            if (fileName.endsWith(".pptx")) return "pptx";
            if (fileName.endsWith(".txt")) return "txt";
            if (fileName.endsWith(".md")) return "markdown";
            return "text";
        }
        */
                    })
                    .inputSchema(JsonSchemaGenerator.generateForType(requireNonNull(typeRef.getType())))
                    .description("Embed all documents in a directory for semantic search. " +
                               "Reads text files (.txt, .md, .java, .py, .js, .ts, .json, .yaml, .xml) " +
                               "and Microsoft Office documents (.docx, .xlsx, .pptx) " +
                               "from the specified directory, splits them into chunks, and creates embeddings. " +
                               "The embeddings are stored and can be searched using the search_embeddings tool. " +
                               "Requires EmbeddingClient and Apache POI to be configured.")
                    .inputType(requireNonNull(typeRef.getType()))
                    .build();
        }

        record SearchEmbeddingsArgs(
                @JsonProperty(required = true)
                @JsonPropertyDescription("Search query to find relevant documents")
                String query,
                @JsonProperty(required = true)
                @JsonPropertyDescription("Directory path where documents were embedded")
                String directoryPath,
                @JsonProperty(defaultValue = "5")
                @JsonPropertyDescription("Maximum number of results to return")
                int maxResults
        ) {}

        ToolCallback searchEmbeddings() {
            final var typeRef = new TypeReference<SearchEmbeddingsArgs>() {};

            return FunctionToolCallback.<SearchEmbeddingsArgs, String>builder(
                    "search_embeddings", (input, context) -> {
                        return "Error: Embedding search functionality requires Spring AI embedding dependencies. " +
                               "Please add spring-ai-openai (or similar) dependency and ensure EmbeddingClient bean is configured.";
                        
                        /* Uncomment when EmbeddingClient is available:
                        DeepAgent.log.info("Searching embeddings for query: '{}' in directory: {}", 
                                input.query(), input.directoryPath());

                        try {
                            // Get vector store for this directory
                            VectorStore vectorStore = vectorStoreCache.get(input.directoryPath());

                            if (vectorStore == null) {
                                return format("Error: No embeddings found for directory '%s'. " +
                                            "Please run embed_documents first for this directory.", 
                                            input.directoryPath());
                            }

                            // Search for similar documents
                            SearchRequest searchRequest = SearchRequest.query(input.query())
                                    .withTopK(input.maxResults());

                            List<Document> results = vectorStore.similaritySearch(searchRequest);

                            if (results.isEmpty()) {
                                return format("No documents found matching query: '%s' in directory: %s", 
                                        input.query(), input.directoryPath());
                            }

                            // Format results
                            StringBuilder resultBuilder = new StringBuilder();
                            resultBuilder.append(format("Found %d relevant documents for query: '%s'\n\n", 
                                    results.size(), input.query()));

                            for (int i = 0; i < results.size(); i++) {
                                Document doc = results.get(i);
                                resultBuilder.append(format("--- Result %d ---\n", i + 1));
                                
                                // Add metadata
                                if (doc.getMetadata().containsKey("filePath")) {
                                    resultBuilder.append(format("File: %s\n", doc.getMetadata().get("filePath")));
                                }
                                
                                // Add content (truncate if too long)
                                String content = doc.getContent();
                                if (content.length() > 2000) {
                                    content = content.substring(0, 2000) + "... (truncated)";
                                }
                                resultBuilder.append(format("Content:\n%s\n\n", content));
                            }

                            return resultBuilder.toString();

                        } catch (Exception e) {
                            DeepAgent.log.error("Error searching embeddings", e);
                            return format("Error searching embeddings: %s", e.getMessage());
                        }
                        */
                    })
                    .inputSchema(JsonSchemaGenerator.generateForType(requireNonNull(typeRef.getType())))
                    .description("Search embedded documents using semantic similarity. " +
                               "Returns the most relevant document chunks for the given query. " +
                               "The directoryPath must match the one used in embed_documents. " +
                               "This tool performs semantic search, so it can find documents even if they don't " +
                               "contain the exact keywords from the query. " +
                               "Requires EmbeddingClient to be configured.")
                    .inputType(requireNonNull(typeRef.getType()))
                    .build();
        }
    }

    List<ToolCallback> BUILTIN =  List.of(
            Tools.ls(),
            Tools.readFile(),
            Tools.writeFile(),
            Tools.editFile(),
            Tools.writeTodos()
    );

    /**
     * Create embed_documents tool
     * Embeds all documents in a directory for semantic search
     * 
     * @param embeddingClient The embedding client to use for creating embeddings
     * @return ToolCallback for embed_documents tool
     * 
     * NOTE: This requires Spring AI embedding dependencies.
     */
    static ToolCallback embedDocuments(Object embeddingClient) {
        return new EmbeddingTools(embeddingClient).embedDocuments();
    }

    /**
     * Create search_embeddings tool
     * Searches embedded documents using semantic similarity
     * 
     * @param embeddingClient The embedding client to use for searching
     * @return ToolCallback for search_embeddings tool
     * 
     * NOTE: This requires Spring AI embedding dependencies.
     */
    static ToolCallback searchEmbeddings(Object embeddingClient) {
        return new EmbeddingTools(embeddingClient).searchEmbeddings();
    }

    /**
     * Create all embedding tools with EmbeddingClient
     * @param embeddingClient The embedding client to use for creating embeddings
     * @return List of embedding-related tools (embed_documents, search_embeddings)
     * 
     * NOTE: This requires Spring AI embedding dependencies.
     * The EmbeddingClient can be obtained from ChatModel configuration.
     * For example, if using OpenAI:
     *   - OpenAiChatModel implements EmbeddingModel (which extends EmbeddingClient)
     *   - Or use OpenAiEmbeddingModel separately
     */
    static List<ToolCallback> embeddingTools(Object embeddingClient) {
        return List.of(
                embedDocuments(embeddingClient),
                searchEmbeddings(embeddingClient)
        );
    }

}
