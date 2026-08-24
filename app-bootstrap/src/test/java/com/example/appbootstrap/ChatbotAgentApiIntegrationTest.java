package com.example.appbootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.example.moduleai.agent.AgentOrchestrator;
import com.example.moduleai.confirmation.ToolConfirmationService;
import com.example.moduleai.confirmation.ToolProposalView;
import com.example.moduleai.dto.AnswerType;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.guardrail.AgentConcurrencyLimiter;
import com.example.moduleai.guardrail.AgentRequestLimiter;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.Role;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChatbotAgentApiIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AgentOrchestrator agentOrchestrator;
  @MockitoBean private ToolConfirmationService confirmationService;
  @MockitoBean private AgentRequestLimiter requestLimiter;
  @MockitoBean private AgentConcurrencyLimiter concurrencyLimiter;

  @Test
  void chatbotMessageRemainsPublicAndReturnsAgentEnvelope() throws Exception {
    AgentConcurrencyLimiter.Slot slot = mock(AgentConcurrencyLimiter.Slot.class);
    when(concurrencyLimiter.acquireUserSlot(null, "chat")).thenReturn(slot);
    when(agentOrchestrator.handle(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(
            new RagChatResponse(
                "Found flights",
                List.of(),
                false,
                null,
                AnswerType.TOOL_RESULT,
                null,
                List.of(),
                0.0));

    mockMvc
        .perform(
            post("/api/chatbot/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Find flights tomorrow\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.answer").value("Found flights"))
        .andExpect(jsonPath("$.data.answerType").value("TOOL_RESULT"));

    verify(requestLimiter).checkQuery(null, null);
    verify(slot).close();
  }

  @Test
  void proposalEndpointsRequireAuthentication() throws Exception {
    UUID proposalId = UUID.randomUUID();

    mockMvc
        .perform(get("/api/chatbot/proposals/{id}", proposalId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/chatbot/proposals/{id}/confirm", proposalId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/chatbot/proposals/{id}/reject", proposalId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedUserCanReadAndConfirmOwnProposal() throws Exception {
    UUID proposalId = UUID.randomUUID();
    AppUserPrincipal principal =
        new AppUserPrincipal(77L, "agent@example.com", "secret", Role.USER, null);
    ToolProposalView pending = proposal(proposalId, "PENDING");
    ToolProposalView executed = proposal(proposalId, "EXECUTED");
    AgentConcurrencyLimiter.Slot slot = mock(AgentConcurrencyLimiter.Slot.class);
    when(confirmationService.getForUser(proposalId, 77L)).thenReturn(pending);
    when(confirmationService.confirm(proposalId, 77L)).thenReturn(executed);
    when(concurrencyLimiter.acquireUserSlot(77L, "confirm")).thenReturn(slot);

    mockMvc
        .perform(get("/api/chatbot/proposals/{id}", proposalId).with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(result -> assertProposalStatus(result.getResponse().getContentAsString(), "PENDING"));

    mockMvc
        .perform(post("/api/chatbot/proposals/{id}/confirm", proposalId).with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(
            result -> assertProposalStatus(result.getResponse().getContentAsString(), "EXECUTED"));

    verify(requestLimiter).checkConfirmation(77L);
    verify(confirmationService).confirm(proposalId, 77L);
    verify(slot).close();
  }

  private ToolProposalView proposal(UUID id, String status) {
    return new ToolProposalView(
        id,
        "confirm_seat_change",
        ToolOperation.FINANCIAL,
        status,
        "Confirm seat change",
        Map.of("bookingId", 10L),
        OffsetDateTime.now().plusMinutes(5),
        "/api/chatbot/proposals/" + id + "/confirm",
        "/api/chatbot/proposals/" + id + "/reject");
  }

  private void assertProposalStatus(String json, String expectedStatus) {
    try {
      assertEquals(expectedStatus, JsonPath.read(json, "$.data.status"));
    } catch (PathNotFoundException ignored) {
      assertEquals(expectedStatus, JsonPath.read(json, "$.status"));
    }
  }
}
