package com.example.moduleai.conversation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatTurnRepository extends JpaRepository<ChatTurnEntity, UUID> {

  Optional<ChatTurnEntity> findByConversationIdAndClientMessageId(
      UUID conversationId, String clientMessageId);

  List<ChatTurnEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

  List<ChatTurnEntity> findByConversationIdAndStatusOrderByCreatedAtDesc(
      UUID conversationId, ChatTurnStatus status);

  List<ChatTurnEntity> findByStatusAndUpdatedAtBefore(ChatTurnStatus status, OffsetDateTime before);
}
