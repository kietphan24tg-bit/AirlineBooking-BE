package com.example.moduleai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class ChatPromptFactoryTest {

  private final ChatPromptFactory promptFactory = new ChatPromptFactory();

  @Test
  void systemPromptContainsCoreRules() {
    String systemPrompt = promptFactory.systemPrompt();

    assertThat(systemPrompt)
        .contains("Chỉ trả lời dựa trên context")
        .contains("chính sách tĩnh, tổng quát")
        .contains("booking, payment, flight, seat, refund")
        .contains("business confirmation")
        .contains("Trả lời cùng ngôn ngữ")
        .contains("English question -> English answer")
        .contains("Không nhắc đến RAG");
  }

  @Test
  void userPromptContainsContextAndQuestion() {
    Document document =
        new Document(
            "Khách có thể đổi ghế trong Manage Booking.",
            Map.of(
                "source_file", "seat-selection-policy.md",
                "heading_path", "Chính sách chọn ghế > Đổi ghế"));

    String userPrompt =
        promptFactory.userPrompt("Tôi đổi ghế được không?", java.util.List.of(document));

    assertThat(userPrompt)
        .contains("Context:")
        .contains("seat-selection-policy.md")
        .contains("Chính sách chọn ghế > Đổi ghế")
        .contains("Khách có thể đổi ghế trong Manage Booking.")
        .contains("Identify the primary language of the user question")
        .contains("context language must never determine")
        .contains("Tôi đổi ghế được không?");
  }

  @Test
  void userPromptDoesNotHardCodeAnOutputLanguage() {
    String prompt = promptFactory.userPrompt("Que signifie PENDING_PAYMENT ?", java.util.List.of());

    assertThat(prompt)
        .contains("Que signifie PENDING_PAYMENT ?")
        .doesNotContain("Required output language");
  }
}
