package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.conversation.ChatConversationRepository;
import com.example.moduleai.conversation.ChatConversationStatus;
import com.example.moduleai.guardrail.AgentPayloadSanitizer;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleai.rollout.AgentRolloutMode;
import com.example.moduleai.rollout.AgentRolloutProperties;
import com.example.moduleai.tool.catalog.ToolCatalog;
import com.example.moduleai.tool.catalog.ToolDescriptor;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolError;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolConfirmationService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ToolCatalog toolCatalog;
  private final ToolExecutionGuard toolExecutionGuard;
  private final ToolProposalRepository proposalRepository;
  private final ToolAuditEventRepository auditEventRepository;
  private final ToolProposalSigner signer;
  private final ToolProposalSnapshotFactory snapshotFactory;
  private final ToolConfirmationRegistry confirmationRegistry;
  private final ToolExecutionReconcilerRegistry reconcilerRegistry;
  private final ChatConversationRepository conversationRepository;
  private final ObjectMapper objectMapper;
  private final AiAgentProperties properties;
  private final AgentRolloutProperties rolloutProperties;
  private final AiObservability observability;
  private final AgentPayloadSanitizer payloadSanitizer;

  public ToolConfirmationService(
      ToolCatalog toolCatalog,
      ToolExecutionGuard toolExecutionGuard,
      ToolProposalRepository proposalRepository,
      ToolAuditEventRepository auditEventRepository,
      ToolProposalSigner signer,
      ToolProposalSnapshotFactory snapshotFactory,
      ToolConfirmationRegistry confirmationRegistry,
      ToolExecutionReconcilerRegistry reconcilerRegistry,
      ChatConversationRepository conversationRepository,
      ObjectMapper objectMapper,
      AiAgentProperties properties,
      AgentRolloutProperties rolloutProperties,
      AiObservability observability,
      AgentPayloadSanitizer payloadSanitizer) {
    this.toolCatalog = toolCatalog;
    this.toolExecutionGuard = toolExecutionGuard;
    this.proposalRepository = proposalRepository;
    this.auditEventRepository = auditEventRepository;
    this.signer = signer;
    this.snapshotFactory = snapshotFactory;
    this.confirmationRegistry = confirmationRegistry;
    this.reconcilerRegistry = reconcilerRegistry;
    this.conversationRepository = conversationRepository;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.rolloutProperties = rolloutProperties;
    this.observability = observability;
    this.payloadSanitizer = payloadSanitizer;
    if (properties.isActionsEnabled()
        && (properties.getConfirmationHmacSecret() == null
            || properties.getConfirmationHmacSecret().length() < 32)) {
      throw new IllegalStateException(
          "APP_AI_AGENT_CONFIRMATION_HMAC_SECRET is required when app.ai.agent.actions-enabled=true");
    }
  }

  @Transactional
  public ToolProposalView createProposal(String toolName, Object input, AgentToolContext context) {
    ensureActionsEnabled();
    ToolDescriptor descriptor = toolCatalog.descriptor(toolName);
    if (descriptor.policy().operation() == ToolOperation.READ
        || descriptor.policy().operation() == ToolOperation.PREVIEW) {
      throw new AppException(
          "PROPOSAL_NOT_REQUIRED", "This tool does not require confirmation", 409);
    }
    if (context.userId() == null) {
      throw new AppException("AUTHENTICATION_REQUIRED", "Authentication is required", 401);
    }
    assertNoActiveProposal(context.userId());
    try {
      UUID proposalId = UUID.randomUUID();
      OffsetDateTime now = OffsetDateTime.now();
      OffsetDateTime expiresAt = signer.normalizeTimestamp(now.plus(properties.getProposalTtl()));
      String proposalIdempotencyKey =
          context.idempotencyKey() == null || context.idempotencyKey().isBlank()
              ? "ai-proposal:" + proposalId
              : context.idempotencyKey();
      AgentToolContext proposalContext =
          new AgentToolContext(
              context.conversationId(),
              context.proposalId(),
              context.userId(),
              context.roles(),
              context.confirmed(),
              context.confirmationToken(),
              proposalIdempotencyKey,
              context.verifiedBookingId(),
              context.rawContext());
      ToolError error = toolExecutionGuard.validateForProposal(toolName, input, proposalContext);
      if (error != null) {
        throw toAppException(error);
      }
      Object canonicalInput = snapshotFactory.canonicalizeInput(toolName, input, proposalContext);
      if (canonicalInput == null) {
        canonicalInput = input;
      }
      String canonicalArguments = objectMapper.writeValueAsString(canonicalInput);
      Map<String, Object> snapshot =
          snapshotFactory.createSnapshot(toolName, canonicalInput, proposalContext);
      ToolProposalEntity proposal = new ToolProposalEntity();
      proposal.setId(proposalId);
      proposal.setUserId(context.userId());
      proposal.setVerifiedBookingId(context.verifiedBookingId());
      proposal.setConversationId(context.conversationId());
      proposal.setToolName(toolName);
      proposal.setOperation(descriptor.policy().operation());
      proposal.setAccess(descriptor.policy().access());
      proposal.setCanonicalArguments(canonicalArguments);
      proposal.setConfirmationSnapshot(objectMapper.writeValueAsString(snapshot));
      proposal.setIdempotencyKey(proposalIdempotencyKey);
      proposal.setStatus(ToolProposalStatus.PENDING);
      proposal.setExpiresAt(expiresAt);
      proposal.setCreatedAt(now);
      proposal.setUpdatedAt(now);
      proposal.setConfirmationHmac(
          signer.sign(
              context.conversationId(),
              proposalId,
              context.userId(),
              toolName,
              descriptor.policy().operation().name(),
              canonicalArguments,
              expiresAt));
      proposalRepository.save(proposal);
      audit(proposal, "CREATED", null, null);
      observability.recordToolProposal(toolName, descriptor.policy().operation().name(), "PENDING");
      return toView(proposal);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to persist proposal", exception);
    }
  }

  @Transactional(readOnly = true)
  public ToolProposalView getForUser(UUID proposalId, Long userId) {
    if (userId == null) {
      throw new AppException("AUTHENTICATION_REQUIRED", "Authentication is required", 401);
    }
    return toView(requireProposal(proposalId, userId));
  }

  @Transactional
  public ToolProposalView reject(UUID proposalId, Long userId) {
    ensureActionsEnabled();
    ToolProposalEntity proposal = requireProposal(proposalId, userId);
    ensureConversationActive(proposal);
    OffsetDateTime now = OffsetDateTime.now();
    if (proposal.getExpiresAt().isBefore(now)) {
      throw new AppException("PROPOSAL_EXPIRED", "Proposal has expired", 410);
    }
    int updated =
        proposalRepository.updateStatusForUser(
            proposalId, userId, ToolProposalStatus.PENDING, ToolProposalStatus.REJECTED, now);
    if (updated != 1) {
      observability.recordConfirmationReplay(proposal.getToolName());
      throw new AppException("PROPOSAL_ALREADY_TERMINAL", "Proposal is no longer pending", 409);
    }
    ToolProposalEntity refreshed = requireProposal(proposalId, userId);
    refreshed.setCompletedAt(now);
    refreshed.setUpdatedAt(now);
    proposalRepository.save(refreshed);
    audit(refreshed, "REJECTED", null, null);
    return toView(refreshed);
  }

  @Transactional
  public ToolProposalView confirm(UUID proposalId, Long userId) {
    ensureActionsEnabled();
    if (rolloutProperties.getMode() == AgentRolloutMode.SHADOW) {
      throw new AppException(
          "AGENT_SHADOW_MODE",
          "Proposal confirmation is disabled while Agent actions are in shadow mode",
          409);
    }
    ToolProposalEntity proposal = requireProposal(proposalId, userId);
    ensureConversationActive(proposal);
    OffsetDateTime now = OffsetDateTime.now();
    if (proposal.getExpiresAt().isBefore(now)) {
      throw new AppException("PROPOSAL_EXPIRED", "Proposal has expired", 410);
    }
    int claimed =
        proposalRepository.updateStatusForUser(
            proposalId, userId, ToolProposalStatus.PENDING, ToolProposalStatus.EXECUTING, now);
    if (claimed != 1) {
      observability.recordConfirmationReplay(proposal.getToolName());
      throw new AppException("PROPOSAL_ALREADY_CLAIMED", "Proposal is no longer pending", 409);
    }
    ToolProposalEntity executing = requireProposal(proposalId, userId);
    long startedAt = System.nanoTime();
    try {
      validateIntegrity(executing);
      ToolDescriptor descriptor = toolCatalog.descriptor(executing.getToolName());
      Object typedInput =
          objectMapper.readValue(executing.getCanonicalArguments(), descriptor.inputType());
      Map<String, Object> originalSnapshot =
          objectMapper.readValue(executing.getConfirmationSnapshot(), MAP_TYPE);
      Map<String, Object> trustedContext = new LinkedHashMap<>();
      if (executing.getConversationId() != null) {
        trustedContext.put(
            AgentToolContext.CONVERSATION_ID_KEY, executing.getConversationId().toString());
      }
      trustedContext.put(AgentToolContext.PROPOSAL_ID_KEY, executing.getId().toString());
      trustedContext.put(AgentToolContext.USER_ID_KEY, executing.getUserId());
      trustedContext.put(AgentToolContext.CONFIRMED_KEY, true);
      trustedContext.put(AgentToolContext.IDEMPOTENCY_KEY, executing.getIdempotencyKey());
      if (executing.getVerifiedBookingId() != null) {
        trustedContext.put(
            AgentToolContext.VERIFIED_BOOKING_ID_KEY, executing.getVerifiedBookingId());
      }
      AgentToolContext executionContext =
          new AgentToolContext(
              executing.getConversationId(),
              executing.getId(),
              executing.getUserId(),
              java.util.Set.of(),
              true,
              null,
              executing.getIdempotencyKey(),
              executing.getVerifiedBookingId(),
              trustedContext);
      confirmationRegistry.revalidate(
          executing.getToolName(), typedInput, originalSnapshot, executionContext);
      Object canonicalInput =
          confirmationRegistry.canonicalize(
              executing.getToolName(), typedInput, originalSnapshot, executionContext);
      if (canonicalInput == null) {
        canonicalInput = typedInput;
      }
      String executionArguments = objectMapper.writeValueAsString(canonicalInput);
      ToolResult<?> result = descriptor.invoke(objectMapper, executionArguments, executionContext);
      Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
      observability.recordConfirmationDuration(duration);
      executing.setCompletedAt(OffsetDateTime.now());
      executing.setUpdatedAt(OffsetDateTime.now());
      if (result.status() == com.example.moduleai.tool.core.ToolResultStatus.SUCCESS) {
        executing.setStatus(ToolProposalStatus.EXECUTED);
        executing.setSafeResult(
            objectMapper.writeValueAsString(payloadSanitizer.sanitize(result.data())));
        audit(executing, "EXECUTED", null, duration);
        observability.recordToolExecution(
            executing.getToolName(), executing.getOperation().name(), "EXECUTED", duration);
      } else if (result.error() != null && "unavailable".equals(result.error().type())) {
        executing.setStatus(ToolProposalStatus.EXECUTION_UNKNOWN);
        executing.setSafeError(
            objectMapper.writeValueAsString(payloadSanitizer.sanitize(result.error())));
        audit(executing, "EXECUTION_UNKNOWN", result.error().code(), duration);
        observability.recordToolExecution(
            executing.getToolName(),
            executing.getOperation().name(),
            "EXECUTION_UNKNOWN",
            duration);
      } else {
        executing.setStatus(ToolProposalStatus.FAILED);
        executing.setSafeError(
            result.error() == null
                ? null
                : objectMapper.writeValueAsString(payloadSanitizer.sanitize(result.error())));
        audit(executing, "FAILED", result.error() == null ? null : result.error().code(), duration);
        observability.recordToolExecution(
            executing.getToolName(), executing.getOperation().name(), "FAILED", duration);
      }
      proposalRepository.save(executing);
      return toView(executing);
    } catch (AppException exception) {
      boolean executionUnknown =
          "PAYMENT_EXECUTION_UNKNOWN".equals(exception.getCode())
              || exception.getStatusCode() >= 500;
      return finalizeExceptionalConfirmation(
          executing,
          executionUnknown ? ToolProposalStatus.EXECUTION_UNKNOWN : ToolProposalStatus.FAILED,
          exception.getCode(),
          exception.getMessage(),
          Duration.ofNanos(System.nanoTime() - startedAt));
    } catch (JsonProcessingException exception) {
      return finalizeExceptionalConfirmation(
          executing,
          ToolProposalStatus.EXECUTION_UNKNOWN,
          "CONFIRMATION_SERIALIZATION_FAILED",
          "Unable to persist confirmation result",
          Duration.ofNanos(System.nanoTime() - startedAt));
    } catch (RuntimeException exception) {
      return finalizeExceptionalConfirmation(
          executing,
          ToolProposalStatus.EXECUTION_UNKNOWN,
          "CONFIRMATION_RUNTIME_FAILURE",
          "Tool execution status is unknown",
          Duration.ofNanos(System.nanoTime() - startedAt));
    }
  }

  @Transactional
  public int expirePending() {
    int expired = 0;
    OffsetDateTime now = OffsetDateTime.now();
    for (ToolProposalEntity proposal :
        proposalRepository.findByStatusAndExpiresAtBefore(ToolProposalStatus.PENDING, now)) {
      proposal.setStatus(ToolProposalStatus.EXPIRED);
      proposal.setUpdatedAt(now);
      proposal.setCompletedAt(now);
      proposalRepository.save(proposal);
      audit(proposal, "EXPIRED", null, null);
      expired++;
    }
    return expired;
  }

  @Transactional
  public int expirePendingForConversation(UUID conversationId) {
    if (conversationId == null) {
      return 0;
    }
    OffsetDateTime now = OffsetDateTime.now();
    var pending =
        proposalRepository.findByConversationIdAndStatus(
            conversationId, ToolProposalStatus.PENDING);
    int expired = 0;
    for (ToolProposalEntity proposal : pending) {
      int claimed =
          proposalRepository.updateStatusForProposal(
              proposal.getId(), ToolProposalStatus.PENDING, ToolProposalStatus.EXPIRED, now);
      if (claimed == 1) {
        proposal.setStatus(ToolProposalStatus.EXPIRED);
        proposal.setCompletedAt(now);
        proposal.setUpdatedAt(now);
        audit(proposal, "EXPIRED", "CONVERSATION_ARCHIVED", null);
        expired++;
      }
    }
    return expired;
  }

  @Transactional
  public int reconcileUnknownExecutions() {
    int updated = 0;
    OffsetDateTime cutoff = OffsetDateTime.now().minus(properties.getExecutingTimeout());
    for (ToolProposalEntity proposal :
        proposalRepository.findByStatusAndClaimedAtBefore(ToolProposalStatus.EXECUTING, cutoff)) {
      proposal.setStatus(ToolProposalStatus.EXECUTION_UNKNOWN);
      proposal.setUpdatedAt(OffsetDateTime.now());
      proposalRepository.save(proposal);
      audit(proposal, "EXECUTION_UNKNOWN_TIMEOUT", "EXECUTION_TIMEOUT", null);
      updated++;
    }
    for (ToolProposalEntity proposal :
        proposalRepository.findByStatus(ToolProposalStatus.EXECUTION_UNKNOWN)) {
      ToolExecutionReconciler reconciler = reconcilerRegistry.get(proposal.getToolName());
      if (reconciler == null) {
        continue;
      }
      ToolExecutionReconciliation reconciliation;
      try {
        reconciliation = reconciler.reconcile(proposal);
      } catch (RuntimeException exception) {
        audit(proposal, "RECONCILE_FAILED", "RECONCILE_UNAVAILABLE", null);
        continue;
      }
      if (reconciliation == null
          || reconciliation.status() == ToolProposalStatus.EXECUTION_UNKNOWN) {
        continue;
      }
      proposal.setStatus(reconciliation.status());
      proposal.setUpdatedAt(OffsetDateTime.now());
      proposal.setCompletedAt(OffsetDateTime.now());
      proposal.setSafeResult(writeSafeJson(reconciliation.safeResult()));
      proposal.setSafeError(writeSafeJson(reconciliation.safeError()));
      proposalRepository.save(proposal);
      audit(
          proposal,
          "RECONCILED_" + reconciliation.status().name(),
          reconciliation.status() == ToolProposalStatus.FAILED ? "RECONCILED_FAILURE" : null,
          null);
      updated++;
    }
    return updated;
  }

  private void assertNoActiveProposal(Long userId) {
    if (userId == null) {
      return;
    }
    if (proposalRepository.existsByUserIdAndStatusIn(
        userId, java.util.List.of(ToolProposalStatus.PENDING, ToolProposalStatus.EXECUTING))) {
      throw new AppException(
          "ACTIVE_PROPOSAL_EXISTS",
          "A previous Agent action is still waiting for confirmation or execution",
          409);
    }
  }

  private ToolProposalEntity requireProposal(UUID proposalId, Long userId) {
    if (userId == null) {
      throw new AppException("AUTHENTICATION_REQUIRED", "Authentication is required", 401);
    }
    return proposalRepository
        .findByIdAndUserId(proposalId, userId)
        .orElseThrow(() -> new AppException("PROPOSAL_NOT_FOUND", "Proposal was not found", 404));
  }

  private void validateIntegrity(ToolProposalEntity proposal) {
    boolean valid =
        signer.matches(
            proposal.getConfirmationHmac(),
            proposal.getConversationId(),
            proposal.getId(),
            proposal.getUserId(),
            proposal.getToolName(),
            proposal.getOperation().name(),
            proposal.getCanonicalArguments(),
            proposal.getExpiresAt());
    if (!valid) {
      observability.recordToolIntegrityFailure(proposal.getToolName());
      throw new AppException("PROPOSAL_HMAC_INVALID", "Proposal integrity check failed", 409);
    }
  }

  private void ensureConversationActive(ToolProposalEntity proposal) {
    if (proposal.getConversationId() == null) {
      return;
    }
    boolean active =
        conversationRepository
            .findById(proposal.getConversationId())
            .map(conversation -> conversation.getStatus() == ChatConversationStatus.ACTIVE)
            .orElse(false);
    if (!active) {
      throw new AppException(
          "PROPOSAL_CONVERSATION_INACTIVE",
          "Proposal belongs to an archived or expired chat session",
          410);
    }
  }

  private ToolProposalView finalizeExceptionalConfirmation(
      ToolProposalEntity proposal,
      ToolProposalStatus status,
      String errorCode,
      String message,
      Duration duration) {
    proposal.setStatus(status);
    proposal.setUpdatedAt(OffsetDateTime.now());
    proposal.setCompletedAt(OffsetDateTime.now());
    try {
      Map<String, Object> safeError = new LinkedHashMap<>();
      safeError.put("code", errorCode);
      safeError.put("message", message == null ? "Unknown tool execution error" : message);
      proposal.setSafeError(objectMapper.writeValueAsString(payloadSanitizer.sanitize(safeError)));
    } catch (JsonProcessingException ignored) {
      proposal.setSafeError("{\"code\":\"" + errorCode + "\"}");
    }
    audit(proposal, status.name(), errorCode, duration);
    observability.recordConfirmationDuration(duration);
    observability.recordToolExecution(
        proposal.getToolName(), proposal.getOperation().name(), status.name(), duration);
    proposalRepository.save(proposal);
    return toView(proposal);
  }

  private ToolProposalView toView(ToolProposalEntity proposal) {
    try {
      Map<String, Object> snapshot =
          objectMapper.readValue(proposal.getConfirmationSnapshot(), MAP_TYPE);
      return new ToolProposalView(
          proposal.getId(),
          proposal.getToolName(),
          proposal.getOperation(),
          proposal.getStatus().name(),
          snapshotFactory.summary(proposal.getToolName(), snapshot),
          snapshot,
          proposal.getExpiresAt(),
          "/api/chatbot/proposals/" + proposal.getId() + "/confirm",
          "/api/chatbot/proposals/" + proposal.getId() + "/reject");
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read proposal snapshot", exception);
    }
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

  private String writeSafeJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(payloadSanitizer.sanitize(value));
    } catch (JsonProcessingException exception) {
      return null;
    }
  }

  private AppException toAppException(ToolError error) {
    int status =
        switch (error.type()) {
          case "validation" -> 400;
          case "unauthorized" -> 401;
          case "forbidden" -> 403;
          case "not_found" -> 404;
          case "conflict" -> 409;
          default -> 503;
        };
    return new AppException(error.code(), error.message(), status);
  }

  private void ensureActionsEnabled() {
    if (!properties.isActionsEnabled()) {
      throw new AppException("AGENT_ACTIONS_DISABLED", "Agent actions are disabled", 503);
    }
  }
}
