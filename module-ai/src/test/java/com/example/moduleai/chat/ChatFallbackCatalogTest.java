package com.example.moduleai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatFallbackCatalogTest {

  private final ChatFallbackCatalog catalog = new ChatFallbackCatalog();

  @Test
  void allFallbackTypesUseEnglish() {
    assertThat(catalog.noContext()).startsWith("I do not currently");
    assertThat(catalog.retrievalUnavailable()).startsWith("I cannot access");
    assertThat(catalog.providerUnavailable()).startsWith("The assistant service");
    assertThat(catalog.emptyResponse()).startsWith("I cannot provide");
  }
}
