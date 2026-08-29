package com.example.moduleai.confirmation;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ToolProposalRepository extends JpaRepository<ToolProposalEntity, UUID> {

  Optional<ToolProposalEntity> findByIdAndUserId(UUID id, Long userId);

  List<ToolProposalEntity> findByConversationIdAndStatus(
      UUID conversationId, ToolProposalStatus status);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update ToolProposalEntity proposal
         set proposal.status = :nextStatus,
             proposal.claimedAt = :now,
             proposal.updatedAt = :now
       where proposal.id = :proposalId
         and proposal.userId = :userId
         and proposal.status = :currentStatus
         and proposal.expiresAt > :now
      """)
  int updateStatusForUser(
      @Param("proposalId") UUID proposalId,
      @Param("userId") Long userId,
      @Param("currentStatus") ToolProposalStatus currentStatus,
      @Param("nextStatus") ToolProposalStatus nextStatus,
      @Param("now") OffsetDateTime now);

  List<ToolProposalEntity> findByStatusAndExpiresAtBefore(
      ToolProposalStatus status, OffsetDateTime now);

  List<ToolProposalEntity> findByStatusAndClaimedAtBefore(
      ToolProposalStatus status, OffsetDateTime claimedBefore);

  List<ToolProposalEntity> findByStatus(ToolProposalStatus status);

  boolean existsByUserIdAndStatusIn(Long userId, Collection<ToolProposalStatus> statuses);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update ToolProposalEntity proposal
         set proposal.status = :nextStatus,
             proposal.completedAt = :now,
             proposal.updatedAt = :now
       where proposal.conversationId = :conversationId
         and proposal.status = :currentStatus
      """)
  int updateStatusForConversation(
      @Param("conversationId") UUID conversationId,
      @Param("currentStatus") ToolProposalStatus currentStatus,
      @Param("nextStatus") ToolProposalStatus nextStatus,
      @Param("now") OffsetDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update ToolProposalEntity proposal
         set proposal.status = :nextStatus,
             proposal.completedAt = :now,
             proposal.updatedAt = :now
       where proposal.id = :proposalId
         and proposal.status = :currentStatus
      """)
  int updateStatusForProposal(
      @Param("proposalId") UUID proposalId,
      @Param("currentStatus") ToolProposalStatus currentStatus,
      @Param("nextStatus") ToolProposalStatus nextStatus,
      @Param("now") OffsetDateTime now);
}
