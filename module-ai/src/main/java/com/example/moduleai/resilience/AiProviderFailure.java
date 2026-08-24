package com.example.moduleai.resilience;

public enum AiProviderFailure {
  RATE_LIMITED("AI_PROVIDER_RATE_LIMITED"),
  CIRCUIT_OPEN("AI_PROVIDER_CIRCUIT_OPEN"),
  CAPACITY_EXHAUSTED("AI_PROVIDER_BUSY"),
  TIMEOUT("AI_PROVIDER_TIMEOUT"),
  UNAVAILABLE("AI_PROVIDER_UNAVAILABLE"),
  ERROR("AI_PROVIDER_ERROR");

  private final String fallbackReason;

  AiProviderFailure(String fallbackReason) {
    this.fallbackReason = fallbackReason;
  }

  public String fallbackReason() {
    return fallbackReason;
  }
}
