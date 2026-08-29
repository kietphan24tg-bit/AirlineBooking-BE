package com.example.moduleai.chat;

import com.example.moduleai.conversation.ChatMemorySnapshot;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleai.rag.RagRetriever;
import com.example.moduleai.resilience.AiProviderException;
import com.example.moduleai.resilience.AiProviderGuard;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);

  private final ChatClient chatClient;
  private final RagRetriever ragRetriever;
  private final ChatPromptFactory chatPromptFactory;
  private final ChatResponseMapper chatResponseMapper;
  private final ChatFallbackCatalog fallbackCatalog;
  private final AiObservability observability;
  private final AiProviderGuard providerGuard;
  private final int topK;

  @Autowired
  public ChatService(
      ChatClient.Builder chatClientBuilder,
      RagRetriever ragRetriever,
      ChatPromptFactory chatPromptFactory,
      ChatResponseMapper chatResponseMapper,
      ChatFallbackCatalog fallbackCatalog,
      AiObservability observability,
      AiProviderGuard providerGuard,
      @Value("${app.ai.rag.top-k:5}") int topK) {
    this(
        chatClientBuilder.build(),
        ragRetriever,
        chatPromptFactory,
        chatResponseMapper,
        fallbackCatalog,
        observability,
        providerGuard,
        topK);
  }

  ChatService(
      ChatClient chatClient,
      RagRetriever ragRetriever,
      ChatPromptFactory chatPromptFactory,
      ChatResponseMapper chatResponseMapper,
      ChatFallbackCatalog fallbackCatalog,
      AiObservability observability,
      AiProviderGuard providerGuard,
      int topK) {
    this.chatClient = chatClient;
    this.ragRetriever = ragRetriever;
    this.chatPromptFactory = chatPromptFactory;
    this.chatResponseMapper = chatResponseMapper;
    this.fallbackCatalog = fallbackCatalog;
    this.observability = observability;
    this.providerGuard = providerGuard;
    this.topK = topK;
  }

  public RagChatResponse chat(ChatRequest request) {
    return chatWithTrace(request).response();
  }

  public RagChatResponse chat(
      String originalQuestion, String retrievalQuestion, ChatMemorySnapshot memory) {
    return chatWithTrace(originalQuestion, retrievalQuestion, memory).response();
  }

  public RagChatTrace chatWithTrace(ChatRequest request) {
    return chatWithTrace(
        request.message().trim(),
        request.message().trim(),
        new ChatMemorySnapshot(null, null, List.of()));
  }

  public RagChatTrace chatWithTrace(
      String originalQuestion, String retrievalQuestion, ChatMemorySnapshot memory) {
    long startedAt = System.nanoTime();
    List<Document> documents;
    try {
      documents =
          providerGuard.executeRetrieval(() -> ragRetriever.retrieve(retrievalQuestion, topK));
    } catch (AiProviderException exception) {
      LOGGER.warn(
          "RAG retrieval failed with category {}: {}",
          exception.failure(),
          exception.getCause() == null ? "unknown" : exception.getCause().getMessage());
      return complete(
          chatResponseMapper.fallback(
              fallbackCatalog.retrievalUnavailable(), exception.failure().fallbackReason()),
          List.of(),
          startedAt);
    } catch (RuntimeException exception) {
      LOGGER.warn("RAG retrieval failed: {}", exception.getMessage(), exception);
      return complete(
          chatResponseMapper.fallback(fallbackCatalog.retrievalUnavailable(), "RETRIEVAL_ERROR"),
          List.of(),
          startedAt);
    }
    if (documents.isEmpty()) {
      return complete(
          chatResponseMapper.fallback(fallbackCatalog.noContext(), "NO_RETRIEVED_CONTEXT"),
          documents,
          startedAt);
    }

    String answer;
    try {
      answer =
          providerGuard.execute(
              () -> callChatModel(originalQuestion, retrievalQuestion, memory, documents));
    } catch (AiProviderException exception) {
      LOGGER.warn(
          "Chat model call failed with category {}: {}",
          exception.failure(),
          exception.getCause() == null ? "unknown" : exception.getCause().getMessage());
      return complete(
          chatResponseMapper.fallback(
              fallbackCatalog.providerUnavailable(),
              documents,
              exception.failure().fallbackReason()),
          documents,
          startedAt);
    }

    if (answer == null || answer.isBlank()) {
      return complete(
          chatResponseMapper.fallback(
              fallbackCatalog.emptyResponse(), documents, "EMPTY_MODEL_RESPONSE"),
          documents,
          startedAt);
    }
    return complete(chatResponseMapper.grounded(answer.trim(), documents), documents, startedAt);
  }

  private RagChatTrace complete(
      RagChatResponse response, List<Document> documents, long startedAtNanos) {
    observability.recordRagResult(
        response, documents.size(), Duration.ofNanos(System.nanoTime() - startedAtNanos));
    return new RagChatTrace(response, documents);
  }

  private String callChatModel(String question, List<Document> documents) {
    return callChatModel(
        question, question, new ChatMemorySnapshot(null, null, List.of()), documents);
  }

  private String callChatModel(
      String originalQuestion,
      String retrievalQuestion,
      ChatMemorySnapshot memory,
      List<Document> documents) {
    return chatClient
        .prompt()
        .system(chatPromptFactory.systemPrompt())
        .user(chatPromptFactory.userPrompt(originalQuestion, retrievalQuestion, memory, documents))
        .call()
        .content();
  }
}
