package com.example.moduleai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.agent.IntentRouter;
import com.example.moduleai.agent.RouteCategory;
import com.example.moduleai.chat.ChatFallbackCatalog;
import com.example.moduleai.chat.ChatPromptFactory;
import com.example.moduleai.chat.ChatResponseMapper;
import com.example.moduleai.chat.ChatService;
import com.example.moduleai.chat.RagChatTrace;
import com.example.moduleai.config.AiProviderHttpClientConfiguration;
import com.example.moduleai.config.AiProviderProperties;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.embedding.EmbeddingService;
import com.example.moduleai.embedding.VectorSearchService;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleai.rag.RagChunker;
import com.example.moduleai.rag.RagIngestionResult;
import com.example.moduleai.rag.RagIngestionService;
import com.example.moduleai.rag.RagPolicyDocumentCatalog;
import com.example.moduleai.rag.RagRetriever;
import com.example.moduleai.resilience.AiProviderGuard;
import com.example.moduleai.validation.RagValidationChecklistParser.ValidationCase;
import com.example.moduleai.validation.RagValidationChecklistParser.ValidationSection;
import com.example.moduleai.validation.RagValidationJudge.JudgeDecision;
import com.example.moduleai.validation.RagValidationJudge.JudgeInput;
import com.example.moduleai.validation.RagValidationJudge.ValidationVerdict;
import com.example.moduleai.validation.RagValidationReportWriter.DraftValidationResult;
import com.example.moduleai.validation.RagValidationReportWriter.RagValidationDraft;
import com.example.moduleai.validation.RagValidationReportWriter.RagValidationRun;
import com.example.moduleai.validation.RagValidationReportWriter.RetrievedChunk;
import com.example.moduleai.validation.RagValidationReportWriter.ValidationMetadata;
import com.example.moduleai.validation.RagValidationReportWriter.ValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("rag-live")
@SpringBootTest(
    classes = RagLiveValidationTest.ValidationApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RagLiveValidationTest {

  private static final Path CHECKLIST =
      Path.of("..", "docs", "specs", "rag", "rag-validation-checklist.md")
          .toAbsolutePath()
          .normalize();
  private static final int TOP_K = Integer.getInteger("rag.validation.top-k", 5);
  private static final int REPEATS = Math.max(1, Integer.getInteger("rag.validation.repeats", 1));
  private static final boolean INCLUDE_VARIANTS =
      Boolean.parseBoolean(System.getProperty("rag.validation.variants", "false"));
  private static final int MAX_CASES =
      Math.max(1, Integer.getInteger("rag.validation.max-cases", Integer.MAX_VALUE));
  private static final Set<String> CASE_IDS =
      Set.of(System.getProperty("rag.validation.case-ids", "").split(","));
  private static final boolean INGEST_POLICIES =
      Boolean.parseBoolean(System.getProperty("rag.validation.ingest", "true"));
  private static final boolean GENERATION_ONLY =
      Boolean.parseBoolean(System.getProperty("rag.validation.generation-only", "false"));
  private static final boolean JUDGE_ONLY =
      Boolean.parseBoolean(System.getProperty("rag.validation.judge-only", "false"));
  private static final String RESUME_FROM =
      System.getProperty("rag.validation.resume-from", "").trim();
  private static final Path REPORT_DIR =
      Path.of(System.getProperty("rag.validation.report-dir", "build/reports/rag-validation"));
  private static final long DELAY_MILLIS =
      Math.max(0L, Long.getLong("rag.validation.delay-millis", 5_000L));
  private static final String CHAT_PROVIDER =
      System.getProperty("rag.validation.chat-provider", "google-genai");
  private static final String CHAT_MODEL =
      System.getProperty("rag.validation.chat-model", "gemini-3.5-flash");

  private final RagIngestionService ingestionService;
  private final ChatService chatService;
  private final IntentRouter intentRouter;
  private final RagValidationJudge judge;
  private final ChatPromptFactory chatPromptFactory;

  @Autowired
  RagLiveValidationTest(
      RagIngestionService ingestionService,
      ChatService chatService,
      IntentRouter intentRouter,
      RagValidationJudge judge,
      ChatPromptFactory chatPromptFactory) {
    this.ingestionService = ingestionService;
    this.chatService = chatService;
    this.intentRouter = intentRouter;
    this.judge = judge;
    this.chatPromptFactory = chatPromptFactory;
  }

  @DynamicPropertySource
  static void liveProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> requiredEnvironment("SPRING_DATASOURCE_URL"));
    registry.add(
        "spring.datasource.username", () -> requiredEnvironment("SPRING_DATASOURCE_USERNAME"));
    registry.add(
        "spring.datasource.password", () -> requiredEnvironment("SPRING_DATASOURCE_PASSWORD"));
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
    registry.add("spring.datasource.hikari.data-source-properties.prepareThreshold", () -> "0");
    registry.add("spring.datasource.hikari.data-source-properties.socketTimeout", () -> "120");
    registry.add("spring.ai.retry.max-attempts", () -> "1");
    registry.add("app.ai.provider.google-http-timeout-enabled", () -> "true");
    registry.add("spring.ai.model.chat", () -> CHAT_PROVIDER);
    registry.add("spring.ai.model.embedding", () -> "google-genai");
    registry.add(
        "spring.autoconfigure.exclude",
        () ->
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration");
    registry.add("spring.ai.google.genai.api-key", () -> requiredEnvironment("GEMINI_KEY"));
    registry.add("spring.ai.google.genai.chat.options.model", () -> CHAT_MODEL);
    registry.add("spring.ai.google.genai.chat.options.temperature", () -> "0.0");
    registry.add("spring.ai.google.genai.chat.options.max-output-tokens", () -> "800");
    registry.add(
        "spring.ai.google.genai.embedding.api-key", () -> requiredEnvironment("GEMINI_KEY"));
    registry.add("spring.ai.google.genai.embedding.text.model", () -> "gemini-embedding-001");
    registry.add("spring.ai.google.genai.embedding.text.task-type", () -> "RETRIEVAL_DOCUMENT");
    registry.add("spring.ai.google.genai.embedding.text.dimensions", () -> "768");
    registry.add("spring.ai.google.genai.embedding.text.auto-truncate", () -> "true");
    registry.add("spring.ai.openai.api-key", () -> optionalEnvironment("GROQ_API_KEY"));
    registry.add(
        "spring.ai.openai.base-url",
        () -> environmentOr("GROQ_BASE_URL", "https://api.groq.com/openai/v1"));
    registry.add("app.ai.provider.timeout", () -> environmentOr("APP_AI_PROVIDER_TIMEOUT", "45s"));
    registry.add("spring.ai.openai.timeout", () -> environmentOr("APP_AI_PROVIDER_TIMEOUT", "45s"));
    registry.add("spring.ai.openai.max-retries", () -> "1");
    registry.add("spring.ai.openai.chat.options.model", () -> CHAT_MODEL);
    registry.add("spring.ai.openai.chat.options.temperature", () -> "0.0");
    registry.add("spring.ai.openai.chat.options.max-tokens", () -> "800");
    registry.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "true");
    registry.add("spring.ai.vectorstore.pgvector.index-type", () -> "HNSW");
    registry.add("spring.ai.vectorstore.pgvector.distance-type", () -> "COSINE_DISTANCE");
    registry.add("spring.ai.vectorstore.pgvector.dimensions", () -> "768");
    registry.add("app.ai.rag.docs-path", () -> "../docs/specs/rag");
    registry.add("app.ai.rag.top-k", () -> Integer.toString(TOP_K));
    registry.add("app.ai.rag.ingest-batch-size", () -> "80");
    registry.add("app.ai.rag.ingest-batch-delay", () -> "65s");
  }

  @Test
  void validatesTheCompleteChecklistAgainstLiveRetrievalAndModel() throws Exception {
    if (GENERATION_ONLY && JUDGE_ONLY) {
      throw new IllegalArgumentException("generation-only and judge-only cannot both be enabled");
    }
    List<ValidationCase> checklistCases = new RagValidationChecklistParser().parse(CHECKLIST);
    RagValidationReportWriter initialWriter = new RagValidationReportWriter();
    RagValidationDraft resumedDraft = readResumeDraft(initialWriter);
    RagValidationReportWriter reportWriter =
        resumedDraft == null
            ? initialWriter
            : new RagValidationReportWriter(REPORT_DIR, resumedDraft.metadata().runId());
    ValidationMetadata currentMetadata = metadata(reportWriter.runId());
    if (resumedDraft != null) {
      validateResumeMetadata(resumedDraft.metadata(), currentMetadata);
    }
    RagIngestionResult ingestion =
        !JUDGE_ONLY && INGEST_POLICIES
            ? ingestionService.ingestProductionPolicies()
            : new RagIngestionResult(0, 0, 768);
    List<ExecutionCase> executions =
        expandExecutions(checklistCases).stream()
            .filter(
                execution -> CASE_IDS.contains("") || CASE_IDS.contains(execution.testCase().id()))
            .limit(MAX_CASES)
            .toList();
    List<DraftValidationResult> drafts =
        mergeDrafts(executions, resumedDraft == null ? List.of() : resumedDraft.results());
    ValidationMetadata metadata = resumedDraft == null ? currentMetadata : resumedDraft.metadata();
    RagValidationDraft draft =
        new RagValidationDraft(
            metadata,
            checklistCases.size(),
            resumedDraft == null ? ingestion.filesRead() : resumedDraft.ingestedFiles(),
            resumedDraft == null ? ingestion.chunksStored() : resumedDraft.ingestedChunks(),
            drafts);
    reportWriter.writeDraft(draft);
    if (GENERATION_ONLY) {
      assertThat(drafts).hasSize(executions.size());
      return;
    }
    Map<String, JudgeDecision> decisions = judge.evaluate(judgeInputs(drafts));
    List<ValidationResult> results = finalizeResults(drafts, decisions);

    reportWriter.write(RagValidationRun.create(draft, results));

    assertThat(results)
        .as("Live report: %s", reportWriter.markdownReport())
        .allMatch(result -> result.verdict() == ValidationVerdict.PASS);
  }

  private RagValidationDraft readResumeDraft(RagValidationReportWriter reportWriter)
      throws IOException {
    if (RESUME_FROM.isBlank()) {
      if (JUDGE_ONLY) {
        throw new IllegalArgumentException("judge-only requires rag.validation.resume-from");
      }
      return null;
    }
    return reportWriter.readDraft(Path.of(RESUME_FROM).toAbsolutePath().normalize());
  }

  private void validateResumeMetadata(ValidationMetadata saved, ValidationMetadata current) {
    boolean sameCandidate =
        saved.commit().equals(current.commit())
            && saved.chatModel().equals(current.chatModel())
            && saved.embeddingModel().equals(current.embeddingModel())
            && saved.topK() == current.topK()
            && saved.repeats() == current.repeats()
            && saved.variants() == current.variants()
            && saved.promptHash().equals(current.promptHash())
            && saved.docsHash().equals(current.docsHash());
    if (!sameCandidate) {
      throw new IllegalStateException(
          "Resume draft does not match the current commit/model/prompt/docs configuration");
    }
  }

  private List<DraftValidationResult> mergeDrafts(
      List<ExecutionCase> executions, List<DraftValidationResult> resumedResults) {
    Map<String, DraftValidationResult> byExecution = new LinkedHashMap<>();
    resumedResults.forEach(result -> byExecution.put(result.executionId(), result));
    List<ExecutionCase> missing =
        executions.stream()
            .filter(execution -> !byExecution.containsKey(execution.executionId()))
            .toList();
    if (JUDGE_ONLY && !missing.isEmpty()) {
      throw new IllegalStateException("Resume draft is missing " + missing.size() + " executions");
    }
    executeCases(missing).forEach(result -> byExecution.put(result.executionId(), result));
    return executions.stream().map(execution -> byExecution.get(execution.executionId())).toList();
  }

  private List<DraftValidationResult> executeCases(List<ExecutionCase> executions) {
    List<DraftValidationResult> drafts = new ArrayList<>();
    for (int index = 0; index < executions.size(); index++) {
      ExecutionCase execution = executions.get(index);
      Instant startedAt = Instant.now();
      try {
        RagChatTrace trace = chatService.chatWithTrace(new ChatRequest(execution.question()));
        RagChatResponse response = trace.response();
        String routeCategory = intentRouter.route(execution.question()).category().name();
        List<RetrievedChunk> retrievedChunks = snapshots(trace.documents());
        drafts.add(
            new DraftValidationResult(
                execution.executionId(),
                execution.testCase().id(),
                execution.variant(),
                execution.run(),
                execution.testCase().section().name(),
                execution.question(),
                execution.testCase().expectedBehavior(),
                routeCategory,
                response.answer(),
                response.grounded(),
                response.fallbackReason(),
                sourceFiles(response, retrievedChunks),
                retrievedChunks,
                null,
                Duration.between(startedAt, Instant.now()).toMillis()));
      } catch (RuntimeException ex) {
        drafts.add(
            new DraftValidationResult(
                execution.executionId(),
                execution.testCase().id(),
                execution.variant(),
                execution.run(),
                execution.testCase().section().name(),
                execution.question(),
                execution.testCase().expectedBehavior(),
                "UNAVAILABLE",
                "",
                false,
                "VALIDATION_EXECUTION_ERROR",
                List.of(),
                List.of(),
                conciseMessage(ex),
                Duration.between(startedAt, Instant.now()).toMillis()));
      }
      if (index + 1 < executions.size()) {
        pauseBetweenCalls();
      }
    }
    return drafts;
  }

  private List<JudgeInput> judgeInputs(List<DraftValidationResult> drafts) {
    return drafts.stream()
        .filter(draft -> draft.executionError() == null)
        .filter(draft -> !"CHAT_MODEL_ERROR".equals(draft.fallbackReason()))
        .map(
            draft ->
                new JudgeInput(
                    draft.executionId(),
                    draft.section(),
                    draft.question(),
                    draft.expectedBehavior(),
                    draft.answer(),
                    draft.sources()))
        .toList();
  }

  private List<ValidationResult> finalizeResults(
      List<DraftValidationResult> drafts, Map<String, JudgeDecision> decisions) {
    List<ValidationResult> results = new ArrayList<>();
    for (DraftValidationResult draft : drafts) {
      JudgeDecision decision = decisionFor(draft, decisions);
      results.add(
          new ValidationResult(
              draft.executionId(),
              draft.caseId(),
              draft.variant(),
              draft.run(),
              draft.section(),
              draft.question(),
              draft.expectedBehavior(),
              draft.routeCategory(),
              draft.answer(),
              draft.grounded(),
              draft.fallbackReason(),
              draft.sources(),
              draft.retrievedChunks(),
              decision.verdict(),
              decision.reason(),
              draft.durationMillis()));
    }
    return List.copyOf(results);
  }

  private JudgeDecision decisionFor(
      DraftValidationResult draft, Map<String, JudgeDecision> modelDecisions) {
    if (draft.executionError() != null) {
      return new JudgeDecision(ValidationVerdict.BLOCKED, draft.executionError());
    }
    if ("CHAT_MODEL_ERROR".equals(draft.fallbackReason())) {
      return new JudgeDecision(
          ValidationVerdict.BLOCKED, "Chat model failed while producing the answer.");
    }
    if (ValidationSection.API.name().equals(draft.section())
        && !RouteCategory.DYNAMIC_API_REQUIRED.name().equals(draft.routeCategory())) {
      return new JudgeDecision(
          ValidationVerdict.FAIL, "Must-call-API question was not classified as dynamic.");
    }
    return modelDecisions.getOrDefault(
        draft.executionId(),
        new JudgeDecision(ValidationVerdict.NEEDS_REVIEW, "No judge decision was produced."));
  }

  private List<ExecutionCase> expandExecutions(List<ValidationCase> cases) {
    List<ExecutionCase> executions = new ArrayList<>();
    for (ValidationCase testCase : cases) {
      List<QuestionVariant> variants = new ArrayList<>();
      variants.add(new QuestionVariant("original", testCase.question()));
      if (INCLUDE_VARIANTS) {
        variants.add(new QuestionVariant("no-accents", removeAccents(testCase.question())));
      }
      for (QuestionVariant variant : variants) {
        for (int run = 1; run <= REPEATS; run++) {
          String executionId = testCase.id() + "/" + variant.name() + "/run-" + run;
          executions.add(
              new ExecutionCase(executionId, testCase, variant.name(), run, variant.question()));
        }
      }
    }
    return List.copyOf(executions);
  }

  private List<RetrievedChunk> snapshots(List<Document> documents) {
    return documents.stream()
        .map(
            document ->
                new RetrievedChunk(
                    metadata(document, "source_file"),
                    metadata(document, "heading_path"),
                    document.getScore(),
                    document.getText()))
        .toList();
  }

  private List<String> sourceFiles(RagChatResponse response, List<RetrievedChunk> retrievedChunks) {
    LinkedHashSet<String> sources = new LinkedHashSet<>();
    response.sources().forEach(source -> sources.add(source.sourceFile()));
    retrievedChunks.forEach(chunk -> sources.add(chunk.sourceFile()));
    return List.copyOf(sources);
  }

  private String metadata(Document document, String key) {
    Object value = document.getMetadata().get(key);
    return value == null ? "" : value.toString();
  }

  private String removeAccents(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
    return normalized.replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D');
  }

  private void pauseBetweenCalls() {
    if (DELAY_MILLIS == 0) {
      return;
    }
    try {
      Thread.sleep(DELAY_MILLIS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Live validation interrupted", ex);
    }
  }

  private String conciseMessage(RuntimeException ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    return message.replaceAll("\\s+", " ").trim();
  }

  private ValidationMetadata metadata(String runId) throws IOException {
    String commit =
        System.getProperty("rag.validation.commit", environmentOr("GITHUB_SHA", "unknown"));
    return new ValidationMetadata(
        runId,
        Instant.now().toString(),
        commit,
        CHAT_MODEL,
        System.getProperty("rag.validation.embedding-model", "gemini-embedding-001"),
        TOP_K,
        REPEATS,
        INCLUDE_VARIANTS,
        sha256(
            chatPromptFactory.systemPrompt() + chatPromptFactory.userPrompt("QUESTION", List.of())),
        productionDocsHash());
  }

  private String productionDocsHash() throws IOException {
    MessageDigest digest = sha256Digest();
    List<String> files =
        RagPolicyDocumentCatalog.productionFiles().stream()
            .sorted(Comparator.naturalOrder())
            .toList();
    for (String file : files) {
      digest.update(file.getBytes(StandardCharsets.UTF_8));
      digest.update(Files.readAllBytes(CHECKLIST.getParent().resolve(file)));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private String sha256(String value) {
    MessageDigest digest = sha256Digest();
    return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String environmentOr(String key, String fallback) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String requiredEnvironment(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required environment variable: " + key);
    }
    return value;
  }

  private static String optionalEnvironment(String key) {
    String value = System.getenv(key);
    return value == null ? "" : value;
  }

  @SpringBootConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import({
    RagChunker.class,
    RagIngestionService.class,
    RagRetriever.class,
    VectorSearchService.class,
    EmbeddingService.class,
    ChatFallbackCatalog.class,
    ChatPromptFactory.class,
    ChatResponseMapper.class,
    ChatService.class,
    AiObservability.class,
    AiProviderHttpClientConfiguration.class,
    AiProviderGuard.class,
    IntentRouter.class,
    RagValidationJudge.class
  })
  @EnableConfigurationProperties(AiProviderProperties.class)
  static class ValidationApplication {}

  private record QuestionVariant(String name, String question) {}

  private record ExecutionCase(
      String executionId, ValidationCase testCase, String variant, int run, String question) {}
}
