package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ToolConfirmationPersistence {

  private final ToolProposalRepository proposalRepository;
  private final ToolAuditEventRepository auditEventRepository;

  ToolConfirmationPersistence(
      ToolProposalRepository proposalRepository, ToolAuditEventRepository auditEventRepository) {
    this.proposalRepository = proposalRepository;
    this.auditEventRepository = auditEventRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ToolProposalEntity claim(UUID proposalId, Long userId, OffsetDateTime now) {
    int claimed =
        proposalRepository.updateStatusForUser(
            proposalId, userId, ToolProposalStatus.PENDING, ToolProposalStatus.EXECUTING, now);
    if (claimed != 1) {
      throw new AppException("PROPOSAL_ALREADY_CLAIMED", "Proposal is no longer pending", 409);
    }
    ToolProposalEntity proposal =
        proposalRepository
            .findByIdAndUserId(proposalId, userId)
            .orElseThrow(
                () -> new AppException("PROPOSAL_NOT_FOUND", "Proposal was not found", 404));
    proposal.setStatus(ToolProposalStatus.EXECUTING);
    proposal.setClaimedAt(now);
    proposal.setUpdatedAt(now);
    return proposal;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ToolProposalEntity complete(
      UUID proposalId,
      Long userId,
      ToolProposalStatus status,
      String safeResult,
      String safeError,
      String eventType,
      String errorCode,
      Duration duration) {
    ToolProposalEntity proposal = requireExecuting(proposalId, userId);
    OffsetDateTime now = OffsetDateTime.now();
    proposal.setStatus(status);
    proposal.setSafeResult(safeResult);
    proposal.setSafeError(safeError);
    proposal.setCompletedAt(now);
    proposal.setUpdatedAt(now);
    proposalRepository.save(proposal);
    audit(proposal, eventType, errorCode, duration);
    return proposal;
  }

  private ToolProposalEntity requireExecuting(UUID proposalId, Long userId) {
    ToolProposalEntity proposal =
        proposalRepository
            .findByIdAndUserId(proposalId, userId)
            .orElseThrow(
                () -> new AppException("PROPOSAL_NOT_FOUND", "Proposal was not found", 404));
    if (proposal.getStatus() != ToolProposalStatus.EXECUTING) {
      throw new AppException("PROPOSAL_ALREADY_CLAIMED", "Proposal is no longer executing", 409);
    }
    return proposal;
  }

  private void audit(
      ToolProposalEntity proposal, String eventType, String errorCode, Duration latency) {
    ToolAuditEventEntity event = new ToolAuditEventEntity();
    event.setProposalId(proposal.getId());
    event.setRequestId(MDC.get("requestId"));
    event.setUserId(proposal.getUserId());
    event.setToolName(proposal.getToolName());
    event.setEventType(eventType);
    event.setStatus(proposal.getStatus().name());
    event.setErrorCode(errorCode);
    event.setLatencyMs(latency == null ? null : latency.toMillis());
    event.setCreatedAt(OffsetDateTime.now());
    auditEventRepository.save(event);
  }
}
