package com.example.moduleai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.dto.RagChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class ChatResponseMapperTest {

  private final ChatResponseMapper mapper = new ChatResponseMapper();

  @Test
  void mapsAndDeduplicatesSources() {
    Document first =
        new Document(
            "Content 1",
            Map.of(
                "source_file", "seat-selection-policy.md",
                "heading_path", "Seat Policy > Seat change"));
    Document duplicate =
        new Document(
            "Content 2",
            Map.of(
                "source_file", "seat-selection-policy.md",
                "heading_path", "Seat Policy > Seat change"));

    RagChatResponse response = mapper.grounded("Answer", List.of(first, duplicate));

    assertThat(response.grounded()).isTrue();
    assertThat(response.fallbackReason()).isNull();
    assertThat(response.sources()).hasSize(1);
    assertThat(response.sources().getFirst().sourceFile()).isEqualTo("seat-selection-policy.md");
    assertThat(response.sources().getFirst().headingPath()).isEqualTo("Seat Policy > Seat change");
  }

  @Test
  void fallbackIsNotGrounded() {
    RagChatResponse response = mapper.fallback("No context", "NO_RETRIEVED_CONTEXT");

    assertThat(response.grounded()).isFalse();
    assertThat(response.sources()).isEmpty();
    assertThat(response.fallbackReason()).isEqualTo("NO_RETRIEVED_CONTEXT");
  }

  @Test
  void fallbackCanKeepRetrievedSources() {
    Document document =
        new Document(
            "Content",
            Map.of(
                "source_file", "seat-selection-policy.md",
                "heading_path", "Seat Policy > Seat change"));

    RagChatResponse response = mapper.fallback("No model", List.of(document), "CHAT_MODEL_ERROR");

    assertThat(response.grounded()).isFalse();
    assertThat(response.sources()).hasSize(1);
    assertThat(response.sources().getFirst().sourceFile()).isEqualTo("seat-selection-policy.md");
    assertThat(response.fallbackReason()).isEqualTo("CHAT_MODEL_ERROR");
  }
}
