package com.example.moduleai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RagValidationChecklistOfflineEvalTest {

  private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();
  private static final Path RAG_DOCS_DIR = REPO_ROOT.resolve("docs/specs/rag");
  private static final Path CHECKLIST_PATH = RAG_DOCS_DIR.resolve("rag-validation-checklist.md");
  private static final Path REPORT_PATH =
      REPO_ROOT.resolve("module-ai/build/reports/rag-validation-offline-report.md");

  private static final int TOP_K = 5;
  private static final Pattern TABLE_ROW =
      Pattern.compile("^\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|$");
  private static final Pattern NUMBER_TOKEN = Pattern.compile("\\b\\d+[\\d./-]*\\b");
  private static final Set<String> STOP_WORDS =
      Set.of(
          "la", "lam", "co", "cac", "cua", "cho", "toi", "ban", "duoc", "khong", "hay", "nao",
          "khi", "neu", "va", "voi", "the", "thi", "trong", "mot", "nhung", "can", "tren", "sau",
          "theo", "ve", "da", "dang", "nay", "kia", "gi", "roi", "tai", "tu", "den", "minh",
          "cung");

  @Test
  void replaysValidationChecklistAndWritesOfflineReport() throws IOException {
    List<ChecklistCase> cases = parseChecklistCases();
    Map<String, String> sourceDocuments = loadProductionDocuments();
    List<ChunkCandidate> chunks = chunkDocuments(sourceDocuments);

    List<ChecklistEvaluation> evaluations = new ArrayList<>();
    for (ChecklistCase testCase : cases) {
      evaluations.add(evaluate(testCase, chunks));
    }

    Files.createDirectories(REPORT_PATH.getParent());
    Files.writeString(REPORT_PATH, renderReport(evaluations), StandardCharsets.UTF_8);

    assertThat(cases).isNotEmpty();
    assertThat(evaluations).hasSameSizeAs(cases);
    assertThat(Files.exists(REPORT_PATH)).isTrue();
  }

  private List<ChecklistCase> parseChecklistCases() throws IOException {
    List<ChecklistCase> cases = new ArrayList<>();
    Section currentSection = null;

    for (String rawLine : Files.readAllLines(CHECKLIST_PATH, StandardCharsets.UTF_8)) {
      String line = rawLine.trim();
      if (line.equals("## Must-answer bằng RAG")) {
        currentSection = Section.RAG;
        continue;
      }
      if (line.equals("## Must-call-API")) {
        currentSection = Section.API;
        continue;
      }
      if (line.equals("## Fallback và không được bịa")) {
        currentSection = Section.FALLBACK;
        continue;
      }
      if (line.equals("## Edge case và regression")) {
        currentSection = Section.EDGE;
        continue;
      }
      if (line.equals("## Đa ngôn ngữ và language consistency")) {
        currentSection = Section.LANGUAGE;
        continue;
      }
      if (currentSection == null || !line.startsWith("|")) {
        continue;
      }
      if (line.startsWith("| ID |") || line.startsWith("| ---")) {
        continue;
      }

      Matcher matcher = TABLE_ROW.matcher(line);
      if (matcher.matches()) {
        cases.add(
            new ChecklistCase(
                currentSection,
                matcher.group(1).trim(),
                matcher.group(2).trim(),
                matcher.group(3).trim()));
      }
    }

    return cases;
  }

  private Map<String, String> loadProductionDocuments() throws IOException {
    Map<String, String> documents = new LinkedHashMap<>();
    for (String fileName : RagPolicyDocumentCatalog.productionFiles()) {
      Path path = RAG_DOCS_DIR.resolve(fileName);
      documents.put(fileName, Files.readString(path, StandardCharsets.UTF_8));
    }
    documents.put(
        "rag-scope-and-fallback.md",
        Files.readString(
            RAG_DOCS_DIR.resolve("rag-scope-and-fallback.md"), StandardCharsets.UTF_8));
    documents.put(
        "chatbot-api-intent-map.md",
        Files.readString(
            RAG_DOCS_DIR.resolve("chatbot-api-intent-map.md"), StandardCharsets.UTF_8));
    return documents;
  }

  private List<ChunkCandidate> chunkDocuments(Map<String, String> sourceDocuments) {
    RagChunker chunker = new RagChunker();
    List<ChunkCandidate> chunks = new ArrayList<>();

    for (Map.Entry<String, String> entry : sourceDocuments.entrySet()) {
      for (RagChunk chunk : chunker.chunk(entry.getKey(), entry.getValue(), 1_500)) {
        String searchable = normalize(chunk.headingPath() + "\n" + chunk.content());
        Set<String> tokens = tokens(searchable);
        Set<String> numbers = numbers(searchable);
        chunks.add(
            new ChunkCandidate(
                chunk.sourceFile(),
                chunk.headingPath(),
                chunk.content(),
                searchable,
                tokens,
                numbers));
      }
    }
    return chunks;
  }

  private ChecklistEvaluation evaluate(ChecklistCase testCase, List<ChunkCandidate> chunks) {
    String normalizedQuestion = normalize(testCase.question());
    Set<String> questionTokens = tokens(normalizedQuestion);
    Set<String> questionNumbers = numbers(normalizedQuestion);

    List<RetrievedChunk> retrieved =
        chunks.stream()
            .map(chunk -> scoreChunk(chunk, questionTokens, questionNumbers))
            .filter(retrievedChunk -> retrievedChunk.score() > 0)
            .sorted(Comparator.comparingInt(RetrievedChunk::score).reversed())
            .limit(TOP_K)
            .toList();

    return switch (testCase.section()) {
      case RAG -> evaluateRagCase(testCase, retrieved);
      case API -> evaluateApiCase(testCase, retrieved);
      case FALLBACK -> evaluateFallbackCase(testCase, retrieved);
      case EDGE -> evaluateEdgeCase(testCase, retrieved);
      case LANGUAGE -> evaluateLanguageCase(testCase, retrieved);
    };
  }

  private ChecklistEvaluation evaluateLanguageCase(
      ChecklistCase testCase, List<RetrievedChunk> retrieved) {
    return new ChecklistEvaluation(
        testCase,
        EvalStatus.NEEDS_REVIEW,
        retrieved,
        "Offline retrieval eval khong the xac minh ngon ngu cua cau tra loi; can live LLM judge.");
  }

  private RetrievedChunk scoreChunk(
      ChunkCandidate chunk, Set<String> questionTokens, Set<String> questionNumbers) {
    int tokenOverlap = overlapSize(questionTokens, chunk.tokens());
    int numberOverlap = overlapSize(questionNumbers, chunk.numbers());
    int score = tokenOverlap + (numberOverlap * 3);
    return new RetrievedChunk(chunk, score);
  }

  private ChecklistEvaluation evaluateRagCase(
      ChecklistCase testCase, List<RetrievedChunk> retrieved) {
    Set<String> expectedNumbers = numbers(normalize(testCase.expectedBehavior()));
    boolean retrievedPolicy = !retrieved.isEmpty();
    boolean numberSupport =
        expectedNumbers.isEmpty() || retrievedSupportsNumbers(retrieved, expectedNumbers);
    EvalStatus status = retrievedPolicy && numberSupport ? EvalStatus.PASS : EvalStatus.FAIL;

    String note;
    if (!retrievedPolicy) {
      note = "Khong retrieve duoc chunk policy nao tu bo docs production.";
    } else if (!numberSupport) {
      note =
          "Co retrieve context nhung cac moc so trong expected behavior chua duoc support ro rang.";
    } else {
      note = "Co context truy nguoc duoc ve policy docs va support duoc cac moc/chu de chinh.";
    }

    return new ChecklistEvaluation(testCase, status, retrieved, note);
  }

  private ChecklistEvaluation evaluateApiCase(
      ChecklistCase testCase, List<RetrievedChunk> retrieved) {
    String note =
        """
        Case nay phai goi API/tool theo checklist. Offline document eval khong thuc thi API nen \
        danh dau FAIL theo thiet ke; live validation se kiem tra RAG khong bia du lieu dong va \
        router phan loai case nay sang DYNAMIC_API_REQUIRED.
        """
            .replace('\n', ' ')
            .trim();
    return new ChecklistEvaluation(testCase, EvalStatus.FAIL, retrieved, note);
  }

  private ChecklistEvaluation evaluateFallbackCase(
      ChecklistCase testCase, List<RetrievedChunk> retrieved) {
    boolean hasScopeGuardrail =
        retrieved.stream()
            .map(RetrievedChunk::chunk)
            .anyMatch(
                chunk ->
                    chunk.sourceFile().equals("rag-scope-and-fallback.md")
                        || chunk.sourceFile().equals("travel-documents-policy.md")
                        || chunk.sourceFile().equals("airport-operations-policy.md"));

    EvalStatus status =
        hasScopeGuardrail || retrieved.isEmpty() ? EvalStatus.PASS : EvalStatus.NEEDS_REVIEW;
    String note =
        status == EvalStatus.PASS
            ? "Case fallback co guardrail hoac khong co production context de khang dinh chac chan."
            : "Retrieve duoc context domain nhung can review thu cong de chac bot khong tra loi qua muc.";

    return new ChecklistEvaluation(testCase, status, retrieved, note);
  }

  private ChecklistEvaluation evaluateEdgeCase(
      ChecklistCase testCase, List<RetrievedChunk> retrieved) {
    Set<String> expectedNumbers = numbers(normalize(testCase.expectedBehavior()));
    boolean retrievedPolicy = !retrieved.isEmpty();
    boolean numberSupport =
        expectedNumbers.isEmpty() || retrievedSupportsNumbers(retrieved, expectedNumbers);
    EvalStatus status;
    String note;

    if (!retrievedPolicy) {
      status = EvalStatus.FAIL;
      note = "Khong retrieve duoc policy context cho edge case nay.";
    } else if (requiresApiReview(testCase)) {
      status = EvalStatus.NEEDS_REVIEW;
      note =
          "Policy co lien quan, nhung edge case nay can API/booking-specific state de khang dinh ket qua cuoi.";
    } else if (!numberSupport) {
      status = EvalStatus.NEEDS_REVIEW;
      note = "Retrieve duoc context nhung moc so/chinh sach can review thu cong.";
    } else {
      status = EvalStatus.PASS;
      note = "Edge case co policy support ro rang trong docs hien tai.";
    }

    return new ChecklistEvaluation(testCase, status, retrieved, note);
  }

  private boolean requiresApiReview(ChecklistCase testCase) {
    String question = normalize(testCase.question());
    return question.contains("booking cua toi")
        || question.contains("booking abc123")
        || question.contains("quote")
        || question.contains("preview")
        || question.contains("con trong")
        || question.contains("cua toi")
        || question.contains("manage booking")
        || question.contains("usd");
  }

  private boolean retrievedSupportsNumbers(
      List<RetrievedChunk> retrieved, Set<String> expectedNumbers) {
    if (expectedNumbers.isEmpty()) {
      return true;
    }

    Set<String> retrievedNumbers = new LinkedHashSet<>();
    for (RetrievedChunk chunk : retrieved) {
      retrievedNumbers.addAll(chunk.chunk().numbers());
    }
    return retrievedNumbers.containsAll(expectedNumbers);
  }

  private int overlapSize(Set<String> left, Set<String> right) {
    int count = 0;
    for (String value : left) {
      if (right.contains(value)) {
        count++;
      }
    }
    return count;
  }

  private String renderReport(List<ChecklistEvaluation> evaluations) {
    Map<Section, Long> totals =
        evaluations.stream()
            .collect(
                Collectors.groupingBy(
                    e -> e.testCase().section(),
                    () -> new EnumMap<>(Section.class),
                    Collectors.counting()));
    Map<EvalStatus, Long> summary =
        evaluations.stream()
            .collect(
                Collectors.groupingBy(
                    ChecklistEvaluation::status,
                    () -> new EnumMap<>(EvalStatus.class),
                    Collectors.counting()));

    StringBuilder report = new StringBuilder();
    report.append("# Offline RAG Validation Report\n\n");
    report.append("Generated from `rag-validation-checklist.md` on 2026-08-14.\n\n");
    report.append(
        "This is an offline deterministic evaluation of the current `module-ai` state.\n");
    report.append(
        "It replays the checklist questions against production RAG documents and current capability boundaries.\n\n");
    report.append("## Summary\n\n");
    report.append("- Total cases: ").append(evaluations.size()).append('\n');
    report.append("- PASS: ").append(summary.getOrDefault(EvalStatus.PASS, 0L)).append('\n');
    report.append("- FAIL: ").append(summary.getOrDefault(EvalStatus.FAIL, 0L)).append('\n');
    report
        .append("- NEEDS_REVIEW: ")
        .append(summary.getOrDefault(EvalStatus.NEEDS_REVIEW, 0L))
        .append("\n\n");

    report.append("## By Section\n\n");
    for (Section section : Section.values()) {
      long sectionTotal = totals.getOrDefault(section, 0L);
      long sectionPass =
          evaluations.stream()
              .filter(e -> e.testCase().section() == section && e.status() == EvalStatus.PASS)
              .count();
      long sectionFail =
          evaluations.stream()
              .filter(e -> e.testCase().section() == section && e.status() == EvalStatus.FAIL)
              .count();
      long sectionReview =
          evaluations.stream()
              .filter(
                  e -> e.testCase().section() == section && e.status() == EvalStatus.NEEDS_REVIEW)
              .count();
      report
          .append("- ")
          .append(section.label)
          .append(": ")
          .append(sectionTotal)
          .append(" cases (PASS ")
          .append(sectionPass)
          .append(", FAIL ")
          .append(sectionFail)
          .append(", NEEDS_REVIEW ")
          .append(sectionReview)
          .append(")\n");
    }

    for (Section section : Section.values()) {
      report.append("\n## ").append(section.label).append("\n\n");
      report.append("| ID | Status | Question | Top sources | Note |\n");
      report.append("| --- | --- | --- | --- | --- |\n");
      for (ChecklistEvaluation evaluation : evaluations) {
        if (evaluation.testCase().section() != section) {
          continue;
        }
        report
            .append("| ")
            .append(evaluation.testCase().id())
            .append(" | ")
            .append(evaluation.status())
            .append(" | ")
            .append(escapePipes(evaluation.testCase().question()))
            .append(" | ")
            .append(escapePipes(renderSources(evaluation.retrieved())))
            .append(" | ")
            .append(escapePipes(evaluation.note()))
            .append(" |\n");
      }
    }

    return report.toString();
  }

  private String renderSources(List<RetrievedChunk> retrieved) {
    if (retrieved.isEmpty()) {
      return "-";
    }
    return retrieved.stream()
        .map(chunk -> chunk.chunk().sourceFile())
        .distinct()
        .limit(3)
        .collect(Collectors.joining(", "));
  }

  private String escapePipes(String value) {
    return value.replace("|", "\\|").replace("\n", " ");
  }

  private String normalize(String text) {
    String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
    normalized = normalized.replaceAll("\\p{M}+", "");
    normalized = normalized.toLowerCase(Locale.ROOT);
    return normalized.replaceAll("[^a-z0-9/ ]+", " ").replaceAll("\\s+", " ").trim();
  }

  private Set<String> tokens(String normalizedText) {
    Set<String> result = new LinkedHashSet<>();
    for (String token : normalizedText.split(" ")) {
      if (token.length() < 2 || STOP_WORDS.contains(token)) {
        continue;
      }
      result.add(token);
    }
    return result;
  }

  private Set<String> numbers(String normalizedText) {
    Set<String> result = new LinkedHashSet<>();
    Matcher matcher = NUMBER_TOKEN.matcher(normalizedText);
    while (matcher.find()) {
      result.add(matcher.group());
    }
    return result;
  }

  private record ChecklistCase(
      Section section, String id, String question, String expectedBehavior) {}

  private record ChunkCandidate(
      String sourceFile,
      String headingPath,
      String content,
      String normalizedText,
      Set<String> tokens,
      Set<String> numbers) {}

  private record RetrievedChunk(ChunkCandidate chunk, int score) {}

  private record ChecklistEvaluation(
      ChecklistCase testCase, EvalStatus status, List<RetrievedChunk> retrieved, String note) {}

  private enum Section {
    RAG("Must-answer bang RAG"),
    API("Must-call-API"),
    FALLBACK("Fallback va khong duoc bia"),
    EDGE("Edge case va regression"),
    LANGUAGE("Da ngon ngu va language consistency");

    private final String label;

    Section(String label) {
      this.label = label;
    }
  }

  private enum EvalStatus {
    PASS,
    FAIL,
    NEEDS_REVIEW
  }
}
