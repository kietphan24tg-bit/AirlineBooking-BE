package com.example.moduleai.chat;

import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.rag.RagRetriever;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

  private static final String FALLBACK_ANSWER =
      "Hiện tôi chưa có đủ thông tin để xác nhận. Bạn nên liên hệ nhân viên hỗ trợ để được kiểm tra chính xác.";
  private static final String FALLBACK_REASON = "NO_RETRIEVED_CONTEXT";

  private final ChatClient chatClient;
  private final RagRetriever ragRetriever;
  private final ChatPromptFactory chatPromptFactory;
  private final ChatResponseMapper chatResponseMapper;
  private final int topK;

  public ChatService(
      ChatClient.Builder chatClientBuilder,
      RagRetriever ragRetriever,
      ChatPromptFactory chatPromptFactory,
      ChatResponseMapper chatResponseMapper,
      @Value("${app.ai.rag.top-k:5}") int topK) {
    this.chatClient = chatClientBuilder.build();
    this.ragRetriever = ragRetriever;
    this.chatPromptFactory = chatPromptFactory;
    this.chatResponseMapper = chatResponseMapper;
    this.topK = topK;
  }

  public RagChatResponse chat(ChatRequest request) {
    String question = request.message().trim();
    List<Document> documents = ragRetriever.retrieve(question, topK);
    if (documents.isEmpty()) {
      return chatResponseMapper.fallback(FALLBACK_ANSWER, FALLBACK_REASON);
    }

    String answer;
    try {
      answer =
          chatClient
              .prompt()
              .system(chatPromptFactory.systemPrompt())
              .user(chatPromptFactory.userPrompt(question, documents))
              .call()
              .content();
    } catch (RuntimeException ex) {
      return chatResponseMapper.fallback(FALLBACK_ANSWER, documents, "CHAT_MODEL_ERROR");
    }

    if (answer == null || answer.isBlank()) {
      return chatResponseMapper.fallback(FALLBACK_ANSWER, documents, "EMPTY_MODEL_RESPONSE");
    }
    return chatResponseMapper.grounded(answer.trim(), documents);
  }
}
