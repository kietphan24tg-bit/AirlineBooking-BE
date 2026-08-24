package com.example.moduleai.controller;

import com.example.moduleai.agent.AgentOrchestrator;
import com.example.moduleai.confirmation.ToolConfirmationService;
import com.example.moduleai.confirmation.ToolProposalView;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.guardrail.AgentConcurrencyLimiter;
import com.example.moduleai.guardrail.AgentRequestLimiter;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/chatbot")
@Tag(name = "Chatbot", description = "RAG chatbot endpoints")
public class ChatbotController {

  private final AgentOrchestrator agentOrchestrator;
  private final ToolConfirmationService toolConfirmationService;
  private final AgentRequestLimiter agentRequestLimiter;
  private final AgentConcurrencyLimiter agentConcurrencyLimiter;

  public ChatbotController(
      AgentOrchestrator agentOrchestrator,
      ToolConfirmationService toolConfirmationService,
      AgentRequestLimiter agentRequestLimiter,
      AgentConcurrencyLimiter agentConcurrencyLimiter) {
    this.agentOrchestrator = agentOrchestrator;
    this.toolConfirmationService = toolConfirmationService;
    this.agentRequestLimiter = agentRequestLimiter;
    this.agentConcurrencyLimiter = agentConcurrencyLimiter;
  }

  @PostMapping("/messages")
  @Operation(
      summary = "Ask the AirlineBooking chatbot",
      description = "Answers general airline policy questions using the production RAG index.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Chatbot answer returned",
        content = @Content(schema = @Schema(implementation = RagChatResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
  })
  RagChatResponse chat(
      @Valid @RequestBody ChatRequest request,
      Authentication authentication,
      @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
      @RequestHeader(value = "X-Real-IP", required = false) String realIp) {
    Long userId = currentUserId(authentication);
    agentRequestLimiter.checkQuery(userId, clientIp(forwardedFor, realIp));
    try (AgentConcurrencyLimiter.Slot ignored =
        agentConcurrencyLimiter.acquireUserSlot(userId, "chat")) {
      return agentOrchestrator.handle(request, authentication);
    }
  }

  @GetMapping("/proposals/{proposalId}")
  ToolProposalView getProposal(@PathVariable UUID proposalId, Authentication authentication) {
    return toolConfirmationService.getForUser(proposalId, currentUserId(authentication));
  }

  @PostMapping("/proposals/{proposalId}/confirm")
  ToolProposalView confirmProposal(@PathVariable UUID proposalId, Authentication authentication) {
    Long userId = currentUserId(authentication);
    agentRequestLimiter.checkConfirmation(userId);
    try (AgentConcurrencyLimiter.Slot ignored =
        agentConcurrencyLimiter.acquireUserSlot(userId, "confirm")) {
      return toolConfirmationService.confirm(proposalId, userId);
    }
  }

  @PostMapping("/proposals/{proposalId}/reject")
  ToolProposalView rejectProposal(@PathVariable UUID proposalId, Authentication authentication) {
    Long userId = currentUserId(authentication);
    agentRequestLimiter.checkConfirmation(userId);
    try (AgentConcurrencyLimiter.Slot ignored =
        agentConcurrencyLimiter.acquireUserSlot(userId, "confirm")) {
      return toolConfirmationService.reject(proposalId, userId);
    }
  }

  private Long currentUserId(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AppUserPrincipal user)) {
      return null;
    }
    return user.getUserId();
  }

  private String clientIp(String forwardedFor, String realIp) {
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",", 2)[0].trim();
    }
    return realIp == null || realIp.isBlank() ? null : realIp.trim();
  }
}
