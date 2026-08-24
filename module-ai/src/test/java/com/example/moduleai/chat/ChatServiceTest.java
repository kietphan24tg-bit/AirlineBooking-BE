package com.example.moduleai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.example.moduleai.config.AiProviderProperties;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleai.rag.RagRetriever;
import com.example.moduleai.resilience.AiProviderException;
import com.example.moduleai.resilience.AiProviderFailure;
import com.example.moduleai.resilience.AiProviderGuard;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock private RagRetriever ragRetriever;

  private ChatClient chatClient;
  private ChatService chatService;
  private AiProviderGuard providerGuard;

  @BeforeEach
  void setUp() {
    chatClient =
        mock(
            ChatClient.class, withSettings().defaultAnswer(org.mockito.Answers.RETURNS_DEEP_STUBS));
    AiProviderProperties properties = new AiProviderProperties();
    properties.setRateLimitPermits(100);
    @SuppressWarnings("unchecked")
    ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider =
        mock(ObjectProvider.class);
    providerGuard = new AiProviderGuard(properties, meterRegistryProvider);
    chatService =
        new ChatService(
            chatClient,
            ragRetriever,
            new ChatPromptFactory(),
            new ChatResponseMapper(),
            new ChatFallbackCatalog(),
            mock(AiObservability.class),
            providerGuard,
            3);
  }

  @AfterEach
  void tearDown() {
    providerGuard.close();
  }

  @Test
  void chatReturnsFallbackWhenNoContextIsRetrieved() {
    when(ragRetriever.retrieve("Check-in online mo khi nao?", 3)).thenReturn(List.of());

    RagChatResponse response = chatService.chat(new ChatRequest("Check-in online mo khi nao?"));

    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("NO_RETRIEVED_CONTEXT");
    assertThat(response.sources()).isEmpty();
    assertThat(response.answer()).startsWith("I do not currently have enough information");
  }

  @Test
  void chatReturnsGroundedAnswerWithDeduplicatedSources() {
    Document first =
        new Document(
            "Check-in online mo truoc 24 gio doi voi chuyen bay noi dia.",
            Map.of(
                "source_file", "checkin-and-boarding-policy.md",
                "heading_path", "Check-in Policy > Domestic"));
    Document duplicate =
        new Document(
            "Dong check-in truoc 40 phut.",
            Map.of(
                "source_file", "checkin-and-boarding-policy.md",
                "heading_path", "Check-in Policy > Domestic"));

    when(ragRetriever.retrieve("Check-in noi dia mo luc nao?", 3))
        .thenReturn(List.of(first, duplicate));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("Check-in online mo truoc 24 gio va dong truoc 40 phut.");

    RagChatResponse response = chatService.chat(new ChatRequest("Check-in noi dia mo luc nao?"));

    assertThat(response.grounded()).isTrue();
    assertThat(response.fallbackReason()).isNull();
    assertThat(response.answer())
        .isEqualTo("Check-in online mo truoc 24 gio va dong truoc 40 phut.");
    assertThat(response.sources()).hasSize(1);
    assertThat(response.sources().getFirst().sourceFile())
        .isEqualTo("checkin-and-boarding-policy.md");
  }

  @Test
  void chatReturnsFallbackWhenModelErrors() {
    Document document =
        new Document(
            "Booking hold mac dinh la 15 phut.",
            Map.of(
                "source_file", "booking-and-payment-policy.md",
                "heading_path", "Booking Hold > Payment"));

    when(ragRetriever.retrieve("Booking duoc giu bao lau?", 3)).thenReturn(List.of(document));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenThrow(new RuntimeException("model unavailable"));

    RagChatResponse response = chatService.chat(new ChatRequest("Booking duoc giu bao lau?"));

    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
    assertThat(response.sources()).hasSize(1);
    assertThat(response.answer()).startsWith("The assistant service");
  }

  @Test
  void chatReturnsFallbackWhenModelRespondsBlank() {
    Document document =
        new Document(
            "Infant khong co ghe rieng.",
            Map.of(
                "source_file", "fare-passenger-and-baggage-policy.md",
                "heading_path", "Passenger Policy > Infant"));

    when(ragRetriever.retrieve("Infant co ghe rieng khong?", 3)).thenReturn(List.of(document));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("   ");

    RagChatResponse response = chatService.chat(new ChatRequest("Infant co ghe rieng khong?"));

    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("EMPTY_MODEL_RESPONSE");
    assertThat(response.sources()).hasSize(1);
  }

  @Test
  void chatUsesConfiguredTopKForRetrieval() {
    when(ragRetriever.retrieve("Seat 12A con trong khong?", 3)).thenReturn(List.of());

    chatService.chat(new ChatRequest("Seat 12A con trong khong?"));

    verify(ragRetriever).retrieve("Seat 12A con trong khong?", 3);
    verify(ragRetriever).retrieve(anyString(), anyInt());
  }

  @Test
  void chatReturnsEnglishFallbackWhenProviderIsUnavailable() {
    Document document = new Document("Online check-in opens 24 hours before departure.");
    when(ragRetriever.retrieve("When does online check-in open?", 3)).thenReturn(List.of(document));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenThrow(new RuntimeException("503 service unavailable"));

    RagChatResponse response = chatService.chat(new ChatRequest("When does online check-in open?"));

    assertThat(response.fallbackReason()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
    assertThat(response.answer()).startsWith("The assistant service");
  }

  @Test
  void chatReturnsEnglishFallbackForFrenchQuestionWhenProviderIsRateLimited() {
    Document document = new Document("L'enregistrement ouvre 24 heures avant le départ.");
    when(ragRetriever.retrieve("Quand ouvre l'enregistrement ?", 3)).thenReturn(List.of(document));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenThrow(new RuntimeException("429 too many requests"));

    RagChatResponse response = chatService.chat(new ChatRequest("Quand ouvre l'enregistrement ?"));

    assertThat(response.fallbackReason()).isEqualTo("AI_PROVIDER_RATE_LIMITED");
    assertThat(response.answer()).startsWith("The assistant service");
  }

  @Test
  void chatReturnsEnglishFallbackWhenRetrievalFails() {
    when(ragRetriever.retrieve("What is the infant baggage allowance?", 3))
        .thenThrow(new RuntimeException("database unavailable"));

    RagChatResponse response =
        chatService.chat(new ChatRequest("What is the infant baggage allowance?"));

    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
    assertThat(response.answer()).startsWith("I cannot access policy information");
    assertThat(response.sources()).isEmpty();
  }

  @Test
  void chatPreservesRetrievalProviderFailureReason() {
    when(ragRetriever.retrieve("What is the infant baggage allowance?", 3))
        .thenThrow(
            new AiProviderException(AiProviderFailure.RATE_LIMITED, new RuntimeException("429")));

    RagChatResponse response =
        chatService.chat(new ChatRequest("What is the infant baggage allowance?"));

    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("AI_PROVIDER_RATE_LIMITED");
    assertThat(response.answer()).startsWith("I cannot access policy information");
    assertThat(response.sources()).isEmpty();
  }
}
