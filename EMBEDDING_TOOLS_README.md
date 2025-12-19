# 문서 임베딩 및 검색 기능 사용 가이드

## 개요

`Tools.java`에 문서 임베딩 및 검색 기능이 추가되었습니다. 이 기능을 사용하려면 Spring AI의 임베딩 관련 의존성이 필요합니다.

## 필요한 의존성

`pom.xml`에 다음 의존성이 이미 포함되어 있습니다:
- `spring-ai-openai` (또는 `spring-ai-ollama`, `spring-ai-vertex-ai-gemini` 등)

추가로 벡터 스토어를 사용하려면:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store</artifactId>
    <optional>true</optional>
</dependency>
```

또는 인메모리 벡터 스토어를 사용하려면 Spring AI Core에 포함되어 있을 수 있습니다.

## 사용 방법

### 1. EmbeddingClient Bean 설정

`ChatModelConfiguration.java`에 EmbeddingClient를 추가:

```java
@Bean
@Profile("openai")
public EmbeddingClient embeddingClient(
        @Value("${spring.ai.openai.api-key}") String apiKey) {
    return new OpenAiEmbeddingModel(
        OpenAiApi.builder().apiKey(apiKey).build()
    );
}
```

### 2. Tools에 임베딩 도구 추가

에이전트를 생성할 때:

```java
var agent = DeepAgent.builder()
    .chatModel(chatModel)
    .tools(List.of(
        // 기존 도구들...
    ))
    .build();

// EmbeddingClient를 가져온 후
List<ToolCallback> embeddingTools = Tools.embeddingTools(embeddingClient);
// embeddingTools를 에이전트에 추가
```

### 3. 사용 가능한 도구

#### `embed_documents`
- **목적**: 특정 디렉토리의 모든 문서를 임베딩
- **파라미터**:
  - `directoryPath`: 문서가 있는 디렉토리 경로
  - `chunkSize`: 텍스트 분할 크기 (기본값: 1000)
  - `chunkOverlap`: 청크 겹침 크기 (기본값: 200)
- **지원 파일 형식**: .txt, .md, .java, .py, .js, .ts, .json, .yaml, .yml, .xml

#### `search_embeddings`
- **목적**: 임베딩된 문서를 의미 기반 검색
- **파라미터**:
  - `query`: 검색 쿼리
  - `directoryPath`: 임베딩된 디렉토리 경로
  - `maxResults`: 최대 결과 수 (기본값: 5)

## 현재 상태

현재 구현은 기본 구조만 포함되어 있으며, Spring AI의 정확한 API에 맞춰 주석을 해제하고 수정해야 합니다.

`Tools.java`의 `EmbeddingTools` 클래스에서 주석 처리된 코드를 확인하고, Spring AI 1.0.0의 실제 API에 맞게 수정하세요.

