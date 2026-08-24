package com.example.moduleai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.validation.RagValidationJudge.ValidationVerdict;
import com.example.moduleai.validation.RagValidationReportWriter.DraftValidationResult;
import com.example.moduleai.validation.RagValidationReportWriter.RagValidationDraft;
import com.example.moduleai.validation.RagValidationReportWriter.RagValidationRun;
import com.example.moduleai.validation.RagValidationReportWriter.ValidationMetadata;
import com.example.moduleai.validation.RagValidationReportWriter.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagValidationReportWriterTest {

  @Test
  void writesAtomicDraftAndReadsItForResume() throws Exception {
    Path temporaryDirectory = temporaryDirectory();
    RagValidationReportWriter writer = new RagValidationReportWriter(temporaryDirectory, "run-123");
    RagValidationDraft draft = draft();

    writer.writeDraft(draft);

    assertThat(writer.draftFile()).exists();
    assertThat(writer.draftFile().resolveSibling("draft.json.tmp")).doesNotExist();
    assertThat(writer.readDraft(writer.draftFile())).isEqualTo(draft);
  }

  @Test
  void isolatesReportsByRunIdAndIncludesReleaseMetadata() throws Exception {
    Path temporaryDirectory = temporaryDirectory();
    RagValidationReportWriter first = new RagValidationReportWriter(temporaryDirectory, "full-run");
    RagValidationReportWriter second =
        new RagValidationReportWriter(temporaryDirectory, "targeted-run");
    RagValidationDraft draft = draft();
    ValidationResult result =
        new ValidationResult(
            "RAG-001/original/run-1",
            "RAG-001",
            "original",
            1,
            "RAG",
            "Question",
            "Expected",
            "STATIC_POLICY_RAG",
            "Answer",
            true,
            null,
            List.of("policy.md"),
            List.of(),
            ValidationVerdict.PASS,
            "Correct",
            25);

    first.write(RagValidationRun.create(draft, List.of(result)));
    second.write(RagValidationRun.create(draft, List.of(result)));

    assertThat(first.markdownReport()).exists();
    assertThat(second.markdownReport()).exists();
    assertThat(first.markdownReport().getParent())
        .isNotEqualTo(second.markdownReport().getParent());
    assertThat(first.markdownReport()).content().contains("Commit: abc123", "Docs hash: docs-hash");
  }

  private RagValidationDraft draft() {
    ValidationMetadata metadata =
        new ValidationMetadata(
            "run-123",
            "2026-08-17T00:00:00Z",
            "abc123",
            "chat-model",
            "embedding-model",
            5,
            1,
            false,
            "prompt-hash",
            "docs-hash");
    DraftValidationResult result =
        new DraftValidationResult(
            "RAG-001/original/run-1",
            "RAG-001",
            "original",
            1,
            "RAG",
            "Question",
            "Expected",
            "STATIC_POLICY_RAG",
            "Answer",
            true,
            null,
            List.of("policy.md"),
            List.of(),
            null,
            25);
    return new RagValidationDraft(metadata, 135, 10, 50, List.of(result));
  }

  private Path temporaryDirectory() throws Exception {
    Path directory =
        Path.of("build", "tmp", "rag-validation-report-writer-test", UUID.randomUUID().toString())
            .toAbsolutePath();
    Files.createDirectories(directory);
    return directory;
  }
}
