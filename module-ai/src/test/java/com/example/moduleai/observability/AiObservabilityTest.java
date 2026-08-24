package com.example.moduleai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.dto.AnswerType;
import com.example.moduleai.dto.RagChatResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiObservabilityTest {

  @Test
  void recordsBoundedRagOutcomeLatencyAndRetrievedDocumentMetrics() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    AiObservability observability = new AiObservability(meterRegistry);

    observability.recordRagResult(
        new RagChatResponse(
            "Fallback",
            List.of(),
            false,
            "NO_RETRIEVED_CONTEXT",
            AnswerType.FALLBACK,
            null,
            List.of(),
            0.0),
        0,
        Duration.ofMillis(25));

    assertThat(
            meterRegistry
                .get("app.ai.rag.requests")
                .tag("outcome", "fallback")
                .tag("fallback_reason", "NO_RETRIEVED_CONTEXT")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.get("app.ai.rag.duration").timer().count()).isEqualTo(1L);
    assertThat(meterRegistry.get("app.ai.rag.retrieved.documents").summary().totalAmount())
        .isZero();
  }

  @Test
  void replacesUnboundedFallbackReasonWithUnknown() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    AiObservability observability = new AiObservability(meterRegistry);

    observability.recordRagResult(
        new RagChatResponse(
            "Fallback",
            List.of(),
            false,
            "raw user supplied reason",
            AnswerType.FALLBACK,
            null,
            List.of(),
            0.0),
        1,
        Duration.ofMillis(10));

    assertThat(
            meterRegistry
                .get("app.ai.rag.requests")
                .tag("fallback_reason", "unknown")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
