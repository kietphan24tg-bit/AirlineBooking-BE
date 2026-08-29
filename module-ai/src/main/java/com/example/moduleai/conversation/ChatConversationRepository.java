package com.example.moduleai.conversation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatConversationRepository extends JpaRepository<ChatConversationEntity, UUID> {

  Optional<ChatConversationEntity> findByIdAndUserId(UUID id, Long userId);

  Optional<ChatConversationEntity> findByIdAndGuestTokenHash(UUID id, String guestTokenHash);

  Optional<ChatConversationEntity> findByUserIdAndStatus(
      Long userId, ChatConversationStatus status);

  Optional<ChatConversationEntity> findByGuestTokenHashAndStatus(
      String guestTokenHash, ChatConversationStatus status);

  @Query(
      """
      select conversation
        from ChatConversationEntity conversation
       where conversation.userId = :userId
       order by conversation.updatedAt desc
      """)
  List<ChatConversationEntity> findAllByUserIdOrderByUpdatedAtDesc(
      @Param("userId") Long userId, Pageable pageable);

  @Query(
      """
      select conversation
        from ChatConversationEntity conversation
       where conversation.guestTokenHash = :guestTokenHash
       order by conversation.updatedAt desc
      """)
  List<ChatConversationEntity> findAllByGuestTokenHashOrderByUpdatedAtDesc(
      @Param("guestTokenHash") String guestTokenHash, Pageable pageable);

  List<ChatConversationEntity> findByStatusAndLastActiveAtBefore(
      ChatConversationStatus status, OffsetDateTime before);

  List<ChatConversationEntity> findByStatusAndArchivedAtBefore(
      ChatConversationStatus status, OffsetDateTime before);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update ChatConversationEntity conversation
         set conversation.summaryStatus = :nextStatus,
             conversation.summaryClaimedAt = :claimedAt,
             conversation.summaryAttempts = conversation.summaryAttempts + 1,
             conversation.updatedAt = :claimedAt
       where conversation.id = :conversationId
         and conversation.summaryStatus = :currentStatus
      """)
  int updateSummaryStatus(
      @Param("conversationId") UUID conversationId,
      @Param("currentStatus") ChatSummaryStatus currentStatus,
      @Param("nextStatus") ChatSummaryStatus nextStatus,
      @Param("claimedAt") OffsetDateTime claimedAt);

  @Query(
      """
      select conversation
        from ChatConversationEntity conversation
       where conversation.summaryStatus = :status
       order by conversation.updatedAt asc
      """)
  List<ChatConversationEntity> findBySummaryStatus(
      @Param("status") ChatSummaryStatus status, Pageable pageable);
}
