package com.example.moduleai.resilience;

public class AiProviderException extends RuntimeException {

  private final AiProviderFailure failure;

  public AiProviderException(AiProviderFailure failure, Throwable cause) {
    super(failure.name(), cause);
    this.failure = failure;
  }

  public AiProviderFailure failure() {
    return failure;
  }
}
