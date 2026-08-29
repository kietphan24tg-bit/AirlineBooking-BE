package com.example.moduleai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank @Size(max = 4_000) String message, @Size(max = 100) String clientMessageId) {

  public ChatRequest(String message) {
    this(message, null);
  }
}
