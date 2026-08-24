package com.example.moduleai.guardrail;

public record GuardrailDecision(boolean blocked, String reasonCode, String userMessage) {

  public static GuardrailDecision allow() {
    return new GuardrailDecision(false, null, null);
  }

  public static GuardrailDecision block(String reasonCode, String userMessage) {
    return new GuardrailDecision(true, reasonCode, userMessage);
  }
}
