package com.example.moduleai.guardrail;

import com.example.moduleai.config.AiAgentGuardrailProperties;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class AgentExecutionBudget {

  private final AiAgentGuardrailProperties properties;

  public AgentExecutionBudget(AiAgentGuardrailProperties properties) {
    this.properties = properties;
  }

  public TurnBudget newTurnBudget() {
    return new TurnBudget(properties.getMaxToolCallsPerTurn());
  }

  public static final class TurnBudget {
    private final int maxToolCalls;
    private final AtomicInteger usedToolCalls = new AtomicInteger();

    private TurnBudget(int maxToolCalls) {
      this.maxToolCalls = Math.max(0, maxToolCalls);
    }

    public boolean tryConsumeToolCall() {
      return tryConsumeToolCalls(1);
    }

    public boolean tryConsumeToolCalls(int requestedCalls) {
      if (requestedCalls <= 0) {
        return true;
      }
      while (true) {
        int current = usedToolCalls.get();
        if (current + requestedCalls > maxToolCalls) {
          return false;
        }
        if (usedToolCalls.compareAndSet(current, current + requestedCalls)) {
          return true;
        }
      }
    }
  }
}
