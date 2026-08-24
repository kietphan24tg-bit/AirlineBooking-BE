package com.example.moduleai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.chat.ChatResponseMapper;
import com.example.moduleai.config.AiAgentGuardrailProperties;
import com.example.moduleai.confirmation.ToolConfirmationService;
import com.example.moduleai.confirmation.ToolProposalView;
import com.example.moduleai.dto.AnswerType;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.guardrail.AgentExecutionBudget;
import com.example.moduleai.guardrail.AgentInputGuard;
import com.example.moduleai.guardrail.AgentPayloadSanitizer;
import com.example.moduleai.guardrail.GuardrailDecision;
import com.example.moduleai.guardrail.ToolAllowlistResolver;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleai.tool.catalog.ToolDescriptor;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolPolicy;
import com.example.moduleai.tool.core.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.Authentication;

class AgentToolOrchestratorTest {

  private final AgentInputGuard inputGuard = mock(AgentInputGuard.class);
  private final AgentRequestContextFactory contextFactory = mock(AgentRequestContextFactory.class);
  private final ToolAllowlistResolver allowlistResolver = mock(ToolAllowlistResolver.class);
  private final ToolConfirmationService confirmationService = mock(ToolConfirmationService.class);
  private final AiObservability observability = mock(AiObservability.class);
  private final AgentPayloadSanitizer payloadSanitizer = mock(AgentPayloadSanitizer.class);
  private final AgentTrustedContextResolver trustedContextResolver =
      mock(AgentTrustedContextResolver.class);
  private final ChatModel chatModel = mock(ChatModel.class);
  private final Authentication authentication = mock(Authentication.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AiAgentGuardrailProperties properties = new AiAgentGuardrailProperties();
  private final AgentToolContext context =
      new AgentToolContext(null, 7L, Set.of(), false, null, null, null, Map.of());

  private AgentToolOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    when(inputGuard.assess(any())).thenReturn(GuardrailDecision.allow());
    when(contextFactory.from(authentication)).thenReturn(context);
    orchestrator =
        new AgentToolOrchestrator(
            inputGuard,
            contextFactory,
            allowlistResolver,
            confirmationService,
            new ChatResponseMapper(),
            objectMapper,
            new AgentExecutionBudget(properties),
            observability,
            payloadSanitizer,
            trustedContextResolver,
            properties,
            chatModel);
  }

  @Test
  void executesReadToolThenReturnsNaturalLanguageAnswer() {
    ToolDescriptor descriptor = descriptor("read_tool", ToolOperation.READ);
    when(allowlistResolver.resolve(any(), any())).thenReturn(Set.of(descriptor));
    when(trustedContextResolver.resolve(any(), any(), any())).thenReturn(context);
    doReturn(ToolResult.success("read_tool", Map.of("status", "ok")))
        .when(descriptor)
        .invoke(any(), any(), any());
    when(payloadSanitizer.sanitize(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(toolCallResponse("read_tool", "{}"))
        .thenReturn(textResponse("The booking is confirmed."));

    var response =
        orchestrator.handle(
            new ChatRequest("Check my booking"), dynamicDecision("booking_lookup"), authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.TOOL_RESULT);
    assertThat(response.answer()).isEqualTo("The booking is confirmed.");
    verify(descriptor).invoke(any(), any(), any());
    verify(confirmationService, never()).createProposal(any(), any(), any());
    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, org.mockito.Mockito.times(2)).call(promptCaptor.capture());
    assertThat(promptCaptor.getAllValues().get(0).getInstructions().get(0))
        .isInstanceOf(SystemMessage.class);
  }

  @Test
  void mutationCreatesProposalWithoutInvokingTool() {
    ToolDescriptor descriptor = descriptor("write_tool", ToolOperation.WRITE);
    when(allowlistResolver.resolve(any(), any())).thenReturn(Set.of(descriptor));
    when(trustedContextResolver.resolve(any(), any(), any())).thenReturn(context);
    ToolProposalView proposal =
        new ToolProposalView(
            UUID.randomUUID(),
            "write_tool",
            ToolOperation.WRITE,
            "PENDING",
            "Confirm write",
            Map.of(),
            OffsetDateTime.now().plusMinutes(5),
            "/confirm",
            "/reject");
    when(confirmationService.createProposal("write_tool", Map.of(), context)).thenReturn(proposal);
    when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("write_tool", "{}"));

    var response =
        orchestrator.handle(
            new ChatRequest("Do the change"), dynamicDecision("booking_lookup"), authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.CONFIRMATION_REQUIRED);
    assertThat(response.proposal()).isEqualTo(proposal);
    verify(descriptor, never()).invoke(any(), any(), any());
  }

  @Test
  void rejectsMultipleReadCallsWhenBudgetIsOne() {
    ToolDescriptor first = descriptor("first_tool", ToolOperation.READ);
    ToolDescriptor second = descriptor("second_tool", ToolOperation.READ);
    when(allowlistResolver.resolve(any(), any())).thenReturn(Set.of(first, second));
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            response(
                AssistantMessage.builder()
                    .toolCalls(
                        List.of(
                            new AssistantMessage.ToolCall("1", "function", "first_tool", "{}"),
                            new AssistantMessage.ToolCall("2", "function", "second_tool", "{}")))
                    .build()));

    var result =
        orchestrator.handle(
            new ChatRequest("Read both"), dynamicDecision("booking_lookup"), authentication);

    assertThat(result.fallbackReason()).isEqualTo("TOOL_CALL_BUDGET_EXCEEDED");
    verify(first, never()).invoke(any(), any(), any());
    verify(second, never()).invoke(any(), any(), any());
  }

  private ToolDescriptor descriptor(String name, ToolOperation operation) {
    ToolDescriptor descriptor = mock(ToolDescriptor.class);
    when(descriptor.name()).thenReturn(name);
    when(descriptor.inputType()).thenReturn((Class) Map.class);
    when(descriptor.policy())
        .thenReturn(
            new ToolPolicy(
                name, operation, ToolAccess.AUTHENTICATED, operation != ToolOperation.READ, false));
    when(descriptor.callback()).thenReturn(mock(ToolCallback.class));
    return descriptor;
  }

  private RoutingDecision dynamicDecision(String intent) {
    return RoutingDecision.api(intent, "test", List.of(), false, false);
  }

  private ChatResponse toolCallResponse(String name, String arguments) {
    return response(
        AssistantMessage.builder()
            .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", name, arguments)))
            .build());
  }

  private ChatResponse textResponse(String text) {
    return response(new AssistantMessage(text));
  }

  private ChatResponse response(AssistantMessage message) {
    return new ChatResponse(List.of(new Generation(message)));
  }
}
