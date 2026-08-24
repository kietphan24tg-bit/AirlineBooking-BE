package com.example.moduleai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.chat.ChatResponseMapper;
import com.example.moduleai.chat.ChatService;
import com.example.moduleai.dto.AnswerType;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

  @Mock private IntentRouter intentRouter;
  @Mock private ChatService chatService;
  @Mock private AgentToolOrchestrator agentToolOrchestrator;

  private final ChatResponseMapper chatResponseMapper = new ChatResponseMapper();
  private final Authentication authentication = mock(Authentication.class);

  @Test
  void delegatesStaticPolicyQuestionsToChatService() {
    AgentOrchestrator orchestrator =
        new AgentOrchestrator(intentRouter, chatService, chatResponseMapper, agentToolOrchestrator);
    ChatRequest request = new ChatRequest("Check-in online mo luc nao?");
    RagChatResponse ragResponse =
        new RagChatResponse(
            "answer", List.of(), true, null, AnswerType.ANSWER, null, List.of(), 0.0);

    when(intentRouter.route(request.message()))
        .thenReturn(RoutingDecision.rag("static_policy_rag"));
    when(chatService.chat(request)).thenReturn(ragResponse);

    RagChatResponse response = orchestrator.handle(request, authentication);

    assertThat(response).isEqualTo(ragResponse);
    verify(chatService).chat(request);
  }

  @Test
  void returnsFallbackForDynamicQuestionWithMissingContext() {
    AgentOrchestrator orchestrator =
        new AgentOrchestrator(intentRouter, chatService, chatResponseMapper, agentToolOrchestrator);
    ChatRequest request = new ChatRequest("Booking cua toi co con han khong?");

    when(intentRouter.route(request.message()))
        .thenReturn(
            RoutingDecision.api(
                "booking_lookup",
                "POST /api/bookings/lookup",
                List.of(RequiredContext.BOOKING_CODE, RequiredContext.BOOKING_VERIFICATION),
                false,
                true));

    RagChatResponse response = orchestrator.handle(request, authentication);

    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("MISSING_REQUIRED_CONTEXT");
    assertThat(response.answer()).contains("ma booking");
    verify(chatService, never()).chat(request);
  }

  @Test
  void returnsUnsafeFallbackForUnsafeCategory() {
    AgentOrchestrator orchestrator =
        new AgentOrchestrator(intentRouter, chatService, chatResponseMapper, agentToolOrchestrator);
    ChatRequest request = new ChatRequest("Cach hack tai khoan nguoi khac?");

    when(intentRouter.route(request.message()))
        .thenReturn(RoutingDecision.fallback("unsafe_topic"));

    RagChatResponse response = orchestrator.handle(request, authentication);

    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("UNSAFE_OR_UNCONFIRMED");
    verify(chatService, never()).chat(request);
    verify(agentToolOrchestrator, never()).handle(any(), any(), any());
  }

  private static <T> T any() {
    return org.mockito.ArgumentMatchers.any();
  }
}
