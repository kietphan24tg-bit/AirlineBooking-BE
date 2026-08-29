package com.example.moduleai.conversation;

import com.example.moduleai.config.AiChatProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class ChatMemoryAssembler {

  private final ChatTurnRepository turnRepository;
  private final ChatMessageRepository messageRepository;
  private final AiChatProperties properties;
  private final ObjectMapper objectMapper;

  public ChatMemoryAssembler(
      ChatTurnRepository turnRepository,
      ChatMessageRepository messageRepository,
      AiChatProperties properties,
      ObjectMapper objectMapper) {
    this.turnRepository = turnRepository;
    this.messageRepository = messageRepository;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public ChatMemorySnapshot assemble(ChatConversationEntity conversation) {
    if (conversation == null || !properties.isMemoryEnabled()) {
      return new ChatMemorySnapshot(null, ChatStructuredState.empty(), List.of());
    }
    List<ChatMessageEntity> recent =
        messageRepository.findRecentEligibleMessages(
            conversation.getId(),
            0L,
            PageRequest.of(0, Math.max(1, properties.getRecentTurns()) * 2));
    recent =
        recent.stream()
            .sorted(Comparator.comparingLong(ChatMessageEntity::getSequenceNumber))
            .toList();

    List<ChatMessageView> bounded = new ArrayList<>();
    int charBudget = Math.max(0, properties.getContextBudgetChars());
    int used = 0;
    for (int i = recent.size() - 1; i >= 0; i--) {
      ChatMessageEntity message = recent.get(i);
      int next = message.getContent() == null ? 0 : message.getContent().length();
      if (used + next > charBudget) {
        break;
      }
      bounded.add(
          0,
          new ChatMessageView(
              message.getSequenceNumber(),
              message.getRole().name(),
              message.getContent(),
              message.getCreatedAt()));
      used += next;
    }

    String summary = conversation.getSummary();
    if (summary != null && summary.length() > properties.getSummaryMaxChars()) {
      summary = summary.substring(0, properties.getSummaryMaxChars());
    }
    return new ChatMemorySnapshot(summary, readState(conversation.getStructuredState()), bounded);
  }

  private ChatStructuredState readState(String json) {
    if (json == null || json.isBlank()) {
      return ChatStructuredState.empty();
    }
    try {
      return objectMapper.readValue(json, ChatStructuredState.class);
    } catch (JsonProcessingException exception) {
      return ChatStructuredState.empty();
    }
  }
}
