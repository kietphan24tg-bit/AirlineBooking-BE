package com.example.moduleai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.moduleai.config.AiChatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatMemoryAssemblerTest {

  @Test
  void assemblesSummaryStateAndOnlyEligibleRecentMessages() {
    ChatTurnRepository turnRepository = Mockito.mock(ChatTurnRepository.class);
    ChatMessageRepository messageRepository = Mockito.mock(ChatMessageRepository.class);
    AiChatProperties properties = new AiChatProperties();
    properties.setMemoryEnabled(true);
    properties.setRecentTurns(2);
    properties.setContextBudgetChars(500);
    properties.setSummaryMaxChars(200);
    ObjectMapper objectMapper = new ObjectMapper();
    ChatMemoryAssembler assembler =
        new ChatMemoryAssembler(turnRepository, messageRepository, properties, objectMapper);

    UUID conversationId = UUID.randomUUID();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(conversationId);
    conversation.setSummary("Existing summary");
    conversation.setStructuredState("{\"selectedFareCode\":\"FLEX\"}");

    ChatTurnEntity eligible = new ChatTurnEntity();
    eligible.setId(UUID.randomUUID());
    eligible.setConversationId(conversationId);
    eligible.setStatus(ChatTurnStatus.COMPLETED);
    eligible.setMemoryEligible(true);

    when(messageRepository.findRecentEligibleMessages(eq(conversationId), eq(0L), any()))
        .thenReturn(
            List.of(
                message(conversationId, eligible.getId(), 3L, ChatMessageRole.ASSISTANT, "Reply"),
                message(conversationId, eligible.getId(), 1L, ChatMessageRole.USER, "Hello")));

    ChatMemorySnapshot snapshot = assembler.assemble(conversation);

    assertThat(snapshot.summary()).isEqualTo("Existing summary");
    assertThat(snapshot.state().selectedFareCode()).isEqualTo("FLEX");
    assertThat(snapshot.recentMessages()).hasSize(2);
    assertThat(snapshot.recentMessages().get(0).content()).isEqualTo("Hello");
    assertThat(snapshot.recentMessages().get(1).content()).isEqualTo("Reply");
  }

  @Test
  void excludesMessageThatExceedsTheEntireContextBudget() {
    ChatTurnRepository turnRepository = Mockito.mock(ChatTurnRepository.class);
    ChatMessageRepository messageRepository = Mockito.mock(ChatMessageRepository.class);
    AiChatProperties properties = new AiChatProperties();
    properties.setContextBudgetChars(4);
    ChatMemoryAssembler assembler =
        new ChatMemoryAssembler(turnRepository, messageRepository, properties, new ObjectMapper());

    UUID conversationId = UUID.randomUUID();
    UUID turnId = UUID.randomUUID();
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(conversationId);
    ChatTurnEntity turn = new ChatTurnEntity();
    turn.setId(turnId);
    turn.setConversationId(conversationId);
    turn.setStatus(ChatTurnStatus.COMPLETED);
    turn.setMemoryEligible(true);

    when(messageRepository.findRecentEligibleMessages(eq(conversationId), eq(0L), any()))
        .thenReturn(List.of(message(conversationId, turnId, 1L, ChatMessageRole.USER, "too long")));

    ChatMemorySnapshot snapshot = assembler.assemble(conversation);

    assertThat(snapshot.recentMessages()).isEmpty();
  }

  private ChatMessageEntity message(
      UUID conversationId, UUID turnId, long sequenceNumber, ChatMessageRole role, String content) {
    ChatMessageEntity message = new ChatMessageEntity();
    message.setConversationId(conversationId);
    message.setTurnId(turnId);
    message.setSequenceNumber(sequenceNumber);
    message.setRole(role);
    message.setContent(content);
    message.setCreatedAt(OffsetDateTime.now());
    return message;
  }
}
