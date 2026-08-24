package com.example.moduleai.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.config.AiAgentGuardrailProperties;
import org.junit.jupiter.api.Test;

class AgentExecutionBudgetTest {

  @Test
  void rejectsBatchThatWouldExceedConfiguredToolCallLimit() {
    AiAgentGuardrailProperties properties = new AiAgentGuardrailProperties();
    properties.setMaxToolCallsPerTurn(1);
    AgentExecutionBudget.TurnBudget budget = new AgentExecutionBudget(properties).newTurnBudget();

    assertThat(budget.tryConsumeToolCalls(2)).isFalse();
    assertThat(budget.tryConsumeToolCall()).isTrue();
    assertThat(budget.tryConsumeToolCall()).isFalse();
  }
}
