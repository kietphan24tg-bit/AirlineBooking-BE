package com.example.moduleai.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.validation.RagValidationReportWriter.DraftValidationResult;
import com.google.genai.errors.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class RagValidationJudgeTest {

  @Test
  void marksWholeBatchBlockedWithoutRetryingEachQuestionOnQuotaFailure() {
    ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(client);
    var response = client.prompt().system(anyString()).user(anyString()).call();
    when(response.content())
        .thenThrow(new RuntimeException(new ApiException(429, "status", "private")));
    var inputs =
        List.of(
            new RagValidationJudge.JudgeInput("first", "RAG", "q", "e", "a", List.of()),
            new RagValidationJudge.JudgeInput("second", "RAG", "q", "e", "a", List.of()));

    var decisions = new RagValidationJudge(builder).evaluate(inputs);

    assertThat(decisions).hasSize(2);
    assertThat(decisions.values())
        .allMatch(value -> value.verdict() == RagValidationJudge.ValidationVerdict.BLOCKED);
    verify(response, times(1)).content();
  }

  @Test
  void doesNotFanOutRetriesWhenProviderIsRateLimitedOrUnavailable() {
    for (int status : List.of(429, 500, 503)) {
      RuntimeException failure =
          new RuntimeException(new ApiException(status, "status", "private"));
      assertThat(RagValidationJudge.isProviderUnavailable(failure)).isTrue();
    }
    assertThat(
            RagValidationJudge.isProviderUnavailable(
                new RuntimeException(new ApiException(400, "status", "private"))))
        .isFalse();
    assertThat(RagValidationJudge.isProviderUnavailable(new RuntimeException("parse error")))
        .isFalse();
  }

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
