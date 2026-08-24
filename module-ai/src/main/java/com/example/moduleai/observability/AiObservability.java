package com.example.moduleai.observability;

import com.example.moduleai.dto.RagChatResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiObservability {

  private static final String NONE = "none";

  private final MeterRegistry meterRegistry;
  private final Timer ragDuration;
  private final DistributionSummary retrievedDocuments;
  private final Timer confirmationDuration;
  private final Timer toolExecutionDuration;

  @Autowired
  public AiObservability(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this(meterRegistryProvider.getIfAvailable());
  }

  AiObservability(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.ragDuration =
        meterRegistry == null ? null : Timer.builder("app.ai.rag.duration").register(meterRegistry);
    this.retrievedDocuments =
        meterRegistry == null
            ? null
            : DistributionSummary.builder("app.ai.rag.retrieved.documents").register(meterRegistry);
    this.confirmationDuration =
        meterRegistry == null
            ? null
            : Timer.builder("app.ai.tool.confirmation.duration").register(meterRegistry);
    this.toolExecutionDuration =
        meterRegistry == null
            ? null
            : Timer.builder("app.ai.tool.execution.duration").register(meterRegistry);
  }

  public void recordRagResult(
      RagChatResponse response, int retrievedDocumentCount, Duration duration) {
    if (meterRegistry == null) {
      return;
    }
    String outcome = response.grounded() ? "grounded" : "fallback";
    String fallbackReason =
        response.fallbackReason() == null ? NONE : safeReason(response.fallbackReason());
    Counter.builder("app.ai.rag.requests")
        .tag("outcome", outcome)
        .tag("fallback_reason", fallbackReason)
        .register(meterRegistry)
        .increment();
    ragDuration.record(duration);
    retrievedDocuments.record(retrievedDocumentCount);
  }

  public void recordToolProposal(String tool, String operation, String status) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.tool.proposals")
        .tag("tool", safeTag(tool))
        .tag("operation", safeTag(operation))
        .tag("status", safeTag(status))
        .register(meterRegistry)
        .increment();
  }

  public void recordToolExecution(String tool, String operation, String status, Duration duration) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.tool.execution.total")
        .tag("tool", safeTag(tool))
        .tag("operation", safeTag(operation))
        .tag("status", safeTag(status))
        .register(meterRegistry)
        .increment();
    toolExecutionDuration.record(duration);
  }

  public void recordToolIntegrityFailure(String tool) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.tool.integrity.failures")
        .tag("tool", safeTag(tool))
        .register(meterRegistry)
        .increment();
  }

  public void recordConfirmationReplay(String tool) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.tool.confirmation.replays")
        .tag("tool", safeTag(tool))
        .register(meterRegistry)
        .increment();
  }

  public void recordConfirmationDuration(Duration duration) {
    if (confirmationDuration != null) {
      confirmationDuration.record(duration);
    }
  }

  public void recordAgentGuardrailBlock(String reasonCode) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.agent.guardrail.blocks")
        .tag("reason", safeReason(reasonCode))
        .register(meterRegistry)
        .increment();
  }

  public void recordAgentAllowlistDeny(String intent) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.agent.allowlist.denied")
        .tag("intent", safeTag(intent))
        .register(meterRegistry)
        .increment();
  }

  public void recordAgentRoute(String route, String tool, String outcome) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.agent.routes")
        .tag("route", safeTag(route))
        .tag("tool", safeTag(tool))
        .tag("outcome", safeTag(outcome))
        .register(meterRegistry)
        .increment();
  }

  public void recordAgentRateLimitBlocked(String action, String dimension) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.agent.rate_limit.blocked")
        .tag("action", safeTag(action))
        .tag("dimension", safeTag(dimension))
        .register(meterRegistry)
        .increment();
  }

  public void recordAgentRateLimitBackendFailure(String action) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("app.ai.agent.rate_limit.backend_failures")
        .tag("action", safeTag(action))
        .register(meterRegistry)
        .increment();
  }

  private String safeReason(String reason) {
    return reason.matches("[A-Z0-9_]+") ? reason : "unknown";
  }

  private String safeTag(String value) {
    if (value == null || value.isBlank()) {
      return NONE;
    }
    return value.replaceAll("[^A-Za-z0-9_]", "_");
  }
}
