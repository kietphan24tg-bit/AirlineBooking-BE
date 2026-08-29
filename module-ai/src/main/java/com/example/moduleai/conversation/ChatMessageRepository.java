package com.example.moduleai.conversation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

  List<ChatMessageEntity> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId);

  @Query(
      """
      select message
        from ChatMessageEntity message
        join ChatTurnEntity turn on turn.id = message.turnId
       where message.conversationId = :conversationId
         and turn.status = com.example.moduleai.conversation.ChatTurnStatus.COMPLETED
         and turn.memoryEligible = true
         and message.sequenceNumber > :afterSequence
       order by message.sequenceNumber desc
      """)
  List<ChatMessageEntity> findRecentEligibleMessages(
      @Param("conversationId") UUID conversationId,
      @Param("afterSequence") long afterSequence,
      Pageable pageable);

  @Query(
      """
      select message
        from ChatMessageEntity message
       where message.conversationId = :conversationId
         and (:beforeSequence is null or message.sequenceNumber < :beforeSequence)
       order by message.sequenceNumber desc
      """)
  List<ChatMessageEntity> findPage(
      @Param("conversationId") UUID conversationId,
      @Param("beforeSequence") Long beforeSequence,
      Pageable pageable);
}
