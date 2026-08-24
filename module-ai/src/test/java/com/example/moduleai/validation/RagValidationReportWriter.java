package com.example.moduleai.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class RagValidationReportWriter {

  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  private final ObjectMapper objectMapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private final Path runDirectory;
  private final Path draftFile;
  private Path markdownReport;
  private Path jsonReport;

  RagValidationReportWriter() {
    this(
        Path.of(System.getProperty("rag.validation.report-dir", "build/reports/rag-validation")),
        System.getProperty("rag.validation.run-id", defaultRunId()));
  }

  RagValidationReportWriter(Path reportRoot, String runId) {
    runDirectory = reportRoot.resolve(safeFileName(runId)).toAbsolutePath().normalize();
    draftFile = runDirectory.resolve("draft.json");
  }

  void writeDraft(RagValidationDraft draft) throws IOException {
    Files.createDirectories(runDirectory);
    writeJsonAtomically(draftFile, draft);
  }

  RagValidationDraft readDraft(Path path) throws IOException {
    return objectMapper.readValue(path.toFile(), RagValidationDraft.class);
  }

  void write(RagValidationRun run) throws IOException {
    Files.createDirectories(runDirectory);
    String timestamp = FILE_TIMESTAMP.format(OffsetDateTime.now());
    markdownReport = runDirectory.resolve("report-" + timestamp + ".md");
    jsonReport = runDirectory.resolve("report-" + timestamp + ".json");
    Files.writeString(markdownReport, markdown(run), StandardCharsets.UTF_8);
    writeJsonAtomically(jsonReport, run);
  }

  Path draftFile() {
    return draftFile;
  }

  String runId() {
    return runDirectory.getFileName().toString();
  }

  Path markdownReport() {
    return markdownReport;
  }

  Path jsonReport() {
    return jsonReport;
  }

  private void writeJsonAtomically(Path target, Object value) throws IOException {
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    objectMapper.writeValue(temporary.toFile(), value);
    try {
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String markdown(RagValidationRun run) {
    Map<RagValidationJudge.ValidationVerdict, Long> summary =
        run.results().stream()
            .collect(
                Collectors.groupingBy(
                    ValidationResult::verdict,
                    () -> new EnumMap<>(RagValidationJudge.ValidationVerdict.class),
                    Collectors.counting()));

    StringBuilder report = new StringBuilder("# Live RAG Validation Report\n\n");
    report.append("Run ID: ").append(run.metadata().runId()).append('\n');
    report.append("Generated at: ").append(run.judgedAt()).append('\n');
    report.append("Commit: ").append(run.metadata().commit()).append('\n');
    report.append("Chat model: ").append(run.metadata().chatModel()).append('\n');
    report.append("Embedding model: ").append(run.metadata().embeddingModel()).append('\n');
    report.append("Prompt hash: ").append(run.metadata().promptHash()).append('\n');
    report.append("Docs hash: ").append(run.metadata().docsHash()).append("\n\n");
    report.append("## Summary\n\n");
    report.append("- Cases in checklist: ").append(run.checklistCaseCount()).append('\n');
    report.append("- Executions: ").append(run.results().size()).append('\n');
    for (RagValidationJudge.ValidationVerdict verdict :
        RagValidationJudge.ValidationVerdict.values()) {
      report
          .append("- ")
          .append(verdict)
          .append(": ")
          .append(summary.getOrDefault(verdict, 0L))
          .append('\n');
    }
    report
        .append("- Ingested policy files: ")
        .append(run.ingestedFiles())
        .append('\n')
        .append("- Ingested chunks: ")
        .append(run.ingestedChunks())
        .append("\n\n");

    report.append("## Results\n\n");
    report.append("| Execution | Section | Verdict | Route | Duration | Sources | Reason |\n");
    report.append("| --- | --- | --- | --- | ---: | --- | --- |\n");
    for (ValidationResult result : run.results()) {
      report
          .append("| ")
          .append(escape(result.executionId()))
          .append(" | ")
          .append(result.section())
          .append(" | ")
          .append(result.verdict())
          .append(" | ")
          .append(escape(result.routeCategory()))
          .append(" | ")
          .append(result.durationMillis())
          .append(" ms | ")
          .append(escape(String.join(", ", result.sources())))
          .append(" | ")
          .append(escape(result.reason()))
          .append(" |\n");
    }

    report.append("\n## Evidence\n");
    for (ValidationResult result : run.results()) {
      report
          .append("\n### ")
          .append(result.executionId())
          .append(" - ")
          .append(result.verdict())
          .append("\n\n")
          .append("**Question:** ")
          .append(result.question())
          .append("\n\n**Expected:** ")
          .append(result.expectedBehavior())
          .append("\n\n**Answer:** ")
          .append(result.answer())
          .append("\n\n**Judge:** ")
          .append(result.reason())
          .append("\n\n**Retrieved chunks:**\n");
      for (RetrievedChunk chunk : result.retrievedChunks()) {
        report
            .append("\n- `")
            .append(chunk.sourceFile())
            .append("` - ")
            .append(chunk.headingPath())
            .append(" (score: ")
            .append(chunk.score())
            .append(")\n\n  > ")
            .append(preview(chunk.content()))
            .append('\n');
      }
    }
    return report.toString();
  }

  private String preview(String content) {
    String compact = content.replaceAll("\\s+", " ").trim();
    String value = compact.length() <= 500 ? compact : compact.substring(0, 500) + "...";
    return value.replace("\n", " ");
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|").replaceAll("\\s+", " ").trim();
  }

  private static String defaultRunId() {
    return FILE_TIMESTAMP.format(OffsetDateTime.now());
  }

  private static String safeFileName(String value) {
    String safe = value.replaceAll("[^A-Za-z0-9._-]", "-");
    if (safe.isBlank()) {
      throw new IllegalArgumentException("RAG validation run ID must not be blank");
    }
    return safe;
  }

  record ValidationMetadata(
      String runId,
      String generatedAt,
      String commit,
      String chatModel,
      String embeddingModel,
      int topK,
      int repeats,
      boolean variants,
      String promptHash,
      String docsHash) {}

  record RagValidationDraft(
      ValidationMetadata metadata,
      int checklistCaseCount,
      int ingestedFiles,
      int ingestedChunks,
      List<DraftValidationResult> results) {

    RagValidationDraft {
      results = List.copyOf(results);
    }
  }

  record DraftValidationResult(
      String executionId,
      String caseId,
      String variant,
      int run,
      String section,
      String question,
      String expectedBehavior,
      String routeCategory,
      String answer,
      boolean grounded,
      String fallbackReason,
      List<String> sources,
      List<RetrievedChunk> retrievedChunks,
      String executionError,
      long durationMillis) {

    DraftValidationResult {
      sources = List.copyOf(sources);
      retrievedChunks = List.copyOf(retrievedChunks);
    }
  }

  record RagValidationRun(
      ValidationMetadata metadata,
      String judgedAt,
      int checklistCaseCount,
      int ingestedFiles,
      int ingestedChunks,
      List<ValidationResult> results) {

    static RagValidationRun create(RagValidationDraft draft, List<ValidationResult> results) {
      return new RagValidationRun(
          draft.metadata(),
          OffsetDateTime.now().toString(),
          draft.checklistCaseCount(),
          draft.ingestedFiles(),
          draft.ingestedChunks(),
          List.copyOf(results));
    }
  }

  record ValidationResult(
      String executionId,
      String caseId,
      String variant,
      int run,
      String section,
      String question,
      String expectedBehavior,
      String routeCategory,
      String answer,
      boolean grounded,
      String fallbackReason,
      List<String> sources,
      List<RetrievedChunk> retrievedChunks,
      RagValidationJudge.ValidationVerdict verdict,
      String reason,
      long durationMillis) {}

  record RetrievedChunk(String sourceFile, String headingPath, Double score, String content) {}
}
