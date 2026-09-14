package com.example.moduleai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.validation.RagValidationReportWriter.DraftValidationResult;
import com.google.genai.errors.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagValidationJudgeTest {

  @Test
  void onlySendsSuccessfullyGeneratedAnswersToTheModelJudge() {
    assertThat(RagLiveValidationTest.requiresJudge(draft(null, null))).isTrue();
    assertThat(RagLiveValidationTest.requiresJudge(draft("AI_PROVIDER_RATE_LIMITED", null)))
        .isFalse();
    assertThat(RagLiveValidationTest.requiresJudge(draft("NO_RETRIEVED_CONTEXT", null))).isTrue();
    assertThat(RagLiveValidationTest.requiresJudge(draft("AUTHENTICATION_REQUIRED", null)))
        .isTrue();
    assertThat(RagLiveValidationTest.requiresJudge(draft(null, "execution failed"))).isFalse();
  }

  @Test
  void reportsWrappedProviderStatusWithoutLeakingProviderMessages() {
    RuntimeException failure =
        new RuntimeException(
            "Failed to generate content: key=secret",
            new ApiException(429, "RESOURCE_EXHAUSTED", "prompt=private key=secret"));

    assertThat(RagValidationJudge.conciseMessage(failure))
        .isEqualTo("RuntimeException -> ApiException[HTTP 429]")
        .doesNotContain("secret", "private", "RESOURCE_EXHAUSTED");
  }

  @Test
  void boundsDiagnosticsForCyclicCauses() {
    RuntimeException first = new RuntimeException("secret");
    RuntimeException second = new RuntimeException("private", first);
    first.initCause(second);

    assertThat(RagValidationJudge.conciseMessage(first).split(" -> ")).hasSize(8);
  }

  private DraftValidationResult draft(String fallbackReason, String executionError) {
    return new DraftValidationResult(
        "RAG-001/original/run-1",
        "RAG-001",
        "original",
        1,
        "RAG",
        "question",
        "expected",
        "STATIC_POLICY_RAG",
        "answer",
        fallbackReason == null,
        fallbackReason,
        List.of(),
        List.of(),
        executionError,
        1L);
  }
}
