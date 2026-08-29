package com.example.moduleai.confirmation;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolConfirmationVerifier;
import com.example.moduleai.tool.core.ToolError;
import com.example.moduleai.tool.core.ToolPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ToolProposalConfirmationVerifier implements ToolConfirmationVerifier {

  private final ToolProposalRepository proposalRepository;
  private final ToolProposalSigner signer;
  private final ObjectMapper objectMapper;

  public ToolProposalConfirmationVerifier(
      ToolProposalRepository proposalRepository,
      ToolProposalSigner signer,
      ObjectMapper objectMapper) {
    this.proposalRepository = proposalRepository;
    this.signer = signer;
    this.objectMapper = objectMapper;
  }

  @Override
  public ToolError verify(ToolPolicy policy, Object input, AgentToolContext context) {
    if (!policy.confirmationRequired()) {
      return null;
    }
    if (context.proposalId() == null || context.userId() == null) {
      return ToolError.forbidden(
          "CONFIRMATION_REQUIRED", "A trusted confirmation proposal is required");
    }
    ToolProposalEntity proposal =
        proposalRepository.findByIdAndUserId(context.proposalId(), context.userId()).orElse(null);
    if (proposal == null) {
      return ToolError.notFound("PROPOSAL_NOT_FOUND", "Proposal was not found");
    }
    if (proposal.getExpiresAt().isBefore(java.time.OffsetDateTime.now())) {
      return ToolError.conflict("PROPOSAL_EXPIRED", "Proposal has expired");
    }
    if (proposal.getStatus() != ToolProposalStatus.EXECUTING) {
      return ToolError.conflict("PROPOSAL_NOT_EXECUTING", "Proposal is not ready to execute");
    }
    if (!policy.tool().equals(proposal.getToolName())) {
      return ToolError.forbidden("PROPOSAL_TOOL_MISMATCH", "Proposal tool does not match");
    }
    String canonicalArguments = serialize(input);
    if (!canonicalArguments.equals(proposal.getCanonicalArguments())) {
      return ToolError.forbidden("PROPOSAL_ARGUMENT_MISMATCH", "Proposal arguments do not match");
    }
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
      return ToolError.forbidden("PROPOSAL_HMAC_INVALID", "Proposal integrity check failed");
    }
    if (proposal.getIdempotencyKey() != null
        && !proposal.getIdempotencyKey().equals(context.idempotencyKey())) {
      return ToolError.forbidden(
          "PROPOSAL_IDEMPOTENCY_MISMATCH", "Proposal idempotency context does not match");
    }
    if (proposal.getConversationId() != null
        && !proposal.getConversationId().equals(context.conversationId())) {
      return ToolError.forbidden(
          "PROPOSAL_CONVERSATION_MISMATCH", "Proposal conversation context does not match");
    }
    return null;
  }

  private String serialize(Object input) {
    try {
      return objectMapper.writeValueAsString(input);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize proposal arguments", exception);
    }
  }
}
