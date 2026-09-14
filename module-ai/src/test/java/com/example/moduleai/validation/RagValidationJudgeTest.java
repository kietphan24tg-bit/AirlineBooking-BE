package com.example.moduleai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ApiException;
import org.junit.jupiter.api.Test;

class RagValidationJudgeTest {

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
}
