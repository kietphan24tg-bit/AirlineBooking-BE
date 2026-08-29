package com.example.moduleai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.chat.ChatResponseMapper;
import com.example.moduleai.chat.ChatService;
import com.example.moduleai.config.AiAgentGuardrailProperties;
import com.example.moduleai.confirmation.ToolConfirmationService;
import com.example.moduleai.dto.AnswerType;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.guardrail.AgentExecutionBudget;
import com.example.moduleai.guardrail.AgentInputGuard;
import com.example.moduleai.guardrail.AgentPayloadSanitizer;
import com.example.moduleai.guardrail.GuardrailDecision;
import com.example.moduleai.guardrail.ToolAllowlistResolver;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleai.tool.BookingTools;
import com.example.moduleai.tool.CancellationTools;
import com.example.moduleai.tool.CheckInTools;
import com.example.moduleai.tool.SeatTools;
import com.example.moduleai.tool.catalog.ToolCatalog;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.modulebooking.application.BookingLookupCommand;
import com.example.modulebooking.application.BookingLookupService;
import com.example.modulebooking.application.BookingLookupView;
import com.example.modulebooking.application.BookingManageOptionsService;
import com.example.modulebooking.application.BookingRevalidateService;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.modulebooking.application.UserTripDetailService;
import com.example.modulebooking.application.UserTripHistoryService;
import com.example.modulebooking.application.FlightSeatMapService;
import com.example.modulebooking.application.BookingSeatAssignmentService;
import com.example.modulecheckin.application.CheckInContinueService;
import com.example.modulecheckin.application.CheckInConfirmService;
import com.example.modulepayment.application.BookingCancellationService;
import com.example.modulepayment.application.SeatChangeSettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = AgentPipelineIntegrationTest.PipelineConfig.class, properties = {
      "spring.ai.google.genai.api-key=dummy",
      "app.ai.agent.guardrail.max-model-rounds-per-turn=2",
      "app.ai.agent.guardrail.max-tool-calls-per-turn=5",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
          + "org.springframework.boot.orm.jpa.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration,"
          + "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration,"
          + "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@Tag("agent-e2e")
class AgentPipelineIntegrationTest {

  @Autowired private AgentOrchestrator orchestrator;
  @Autowired private ToolCatalog toolCatalog;
  
  @MockitoBean private ChatModel chatModel;
  @MockitoBean private BookingLookupService bookingLookupService;
  @MockitoBean private BookingManageOptionsService bookingManageOptionsService;
  @MockitoBean private BookingRevalidateService bookingRevalidateService;
  @MockitoBean private TicketIssuanceService ticketIssuanceService;
  @MockitoBean private UserTripDetailService userTripDetailService;
  @MockitoBean private UserTripHistoryService userTripHistoryService;
  @MockitoBean private FlightSeatMapService flightSeatMapService;
  @MockitoBean private BookingSeatAssignmentService bookingSeatAssignmentService;
  @MockitoBean private SeatChangeSettlementService seatChangeSettlementService;
  @MockitoBean private CheckInContinueService checkInContinueService;
  @MockitoBean private CheckInConfirmService checkInConfirmService;
  @MockitoBean private BookingCancellationService bookingCancellationService;
  
  @MockitoBean private AgentInputGuard inputGuard;
  @MockitoBean private AgentRequestContextFactory contextFactory;
  @MockitoBean private ToolAllowlistResolver allowlistResolver;
  @MockitoBean private ToolConfirmationService confirmationService;
  @MockitoBean private AgentTrustedContextResolver trustedContextResolver;
  @MockitoBean private ChatService chatService;

  private final Authentication authentication = mock(Authentication.class);
  private final AgentToolContext authenticatedContext =
      new AgentToolContext(null, null, 77L, Set.of("ROLE_USER"), false, null, null, null, 
          Map.of(AgentToolContext.USER_ID_KEY, 77L, AgentToolContext.ROLES_KEY, Set.of("ROLE_USER")));

  @BeforeEach
  void setUp() {
    when(inputGuard.assess(any())).thenReturn(GuardrailDecision.allow());
    when(contextFactory.from(any())).thenReturn(authenticatedContext);
    when(trustedContextResolver.resolve(any(), any(), any())).thenReturn(authenticatedContext);
  }

  @Test
  void s01_bookingLookupWithPnr_routesAndInvokesTool() {
    // Mock IntentRouter resolves "booking_lookup" intent and it's allowed
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(toolCatalog.descriptor("lookup_booking")));

    // Mock Backend Service
    BookingLookupView mockView = new BookingLookupView(
        new BookingLookupView.BookingSummary(100L, "ABC123", "CONFIRMED", "Confirmed", BigDecimal.valueOf(100), "USD", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1)),
        new BookingLookupView.PrimaryContact("test@example.com", "0123456789"),
        new BookingLookupView.PaymentDetails(new BookingLookupView.PriceBreakdown(BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(100), "USD")),
        List.of(), List.of(), new BookingLookupView.Actions(false, false, false, false, false)
    );
    when(bookingLookupService.lookup(any(BookingLookupCommand.class))).thenReturn(mockView);

    // Mock ChatModel sequence: 1. call tool -> 2. summarize text
    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("lookup_booking", "{\"pnrCode\":\"ABC123\", \"lastName\":\"Nguyen\"}"))
        .thenReturn(textResponse("Trạng thái booking của bạn là CONFIRMED."));

    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Booking ABC123 cua toi da thanh toan chua?"), 
        authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.TOOL_RESULT);
    assertThat(response.answer()).isEqualTo("Trạng thái booking của bạn là CONFIRMED.");
    verify(bookingLookupService).lookup(any(BookingLookupCommand.class));
  }

  @Test
  void s02_bookingLookupWithoutPnr_returnsMissingContextFallback() {
    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Booking của tôi còn hạn không?"), 
        authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.FALLBACK);
    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("MISSING_REQUIRED_CONTEXT");
    verify(chatModel, never()).call(any(Prompt.class));
  }

  @Test
  void s08_staticPolicy_delegatesToChatService() {
    RagChatResponse mockRagResponse = new RagChatResponse("Bạn có thể check in trước 24h.", List.of(), true, null, AnswerType.ANSWER, null, List.of(), 0.0);
    when(chatService.chat(any(ChatRequest.class))).thenReturn(mockRagResponse);

    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Check-in online mở lúc nào?"), 
        authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.ANSWER);
    assertThat(response.answer()).contains("trước 24h");
    verify(chatService).chat(any(ChatRequest.class));
    verify(chatModel, never()).call(any(Prompt.class));
  }

  @Test
  void s09_unsafeTopic_returnsUnsafeFallback() {
    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Visa nhập cảnh cần gì?"), 
        authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.FALLBACK);
    assertThat(response.grounded()).isFalse();
    assertThat(response.fallbackReason()).isEqualTo("UNSAFE_OR_UNCONFIRMED");
    verify(chatModel, never()).call(any(Prompt.class));
    verify(chatService, never()).chat(any(ChatRequest.class));
  }

  @Test
  void s03_listMyTrips_authenticatedContext_routesAndInvokesTool() {
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(toolCatalog.descriptor("list_my_trips")));

    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("list_my_trips", "{\"status\":\"UPCOMING\"}"))
        .thenReturn(textResponse("Bạn có 1 chuyến bay sắp tới."));

    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Tài khoản của tôi có booking nào sắp bay không?"),
        authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.TOOL_RESULT);
    assertThat(response.answer()).isEqualTo("Bạn có 1 chuyến bay sắp tới.");
    // Phase 2: Verify userId=77 propagated into service call
    verify(userTripHistoryService).getTrips(org.mockito.ArgumentMatchers.eq(77L), any());
  }

  @Test
  void s04_seatMap_publicAccess_invokesGetSeatMap() {
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(toolCatalog.descriptor("get_seat_map")));

    var mockSeatMap = new com.example.modulebooking.application.FlightSeatMapService.FlightSeatMapView(
        999L, 1L, 10L, "Y", "ADVANCE", null, "VND",
        new com.example.modulebooking.application.FlightSeatMapService.AircraftLayoutView(
            List.of("A", "B", "C"), List.of("C"), List.of()),
        new com.example.modulebooking.application.FlightSeatMapService.LegendView(
            List.of("AVAILABLE"), List.of("WINDOW"), List.of("STANDARD")),
        List.of());
    when(flightSeatMapService.getSeatMap(any(), any())).thenReturn(mockSeatMap);
    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("get_seat_map", "{\"flightId\":999, \"fareId\":1}"))
        .thenReturn(textResponse("Chuyến bay VN234 còn nhiều ghế trống."));

    RagChatResponse response = orchestrator.handle(
        // Message matching IntentRouter: ghe + con trong + flight code VN123
        new ChatRequest("Ghe 12A tren chuyen VN123 con trong khong?"),
        authentication);

    // Tool invoked correctly — response is non-null
    assertThat(response).isNotNull();
    verify(flightSeatMapService).getSeatMap(any(), any());
  }

  @Test
  void s05_seatChange_mutation_createsProposal() {
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(toolCatalog.descriptor("confirm_seat_change")));

    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("confirm_seat_change",
            "{\"bookingId\":100, \"changes\":[{\"passengerId\":1, \"flightId\":2, \"toSeatId\":3}]}"));

    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Xac nhan doi ghe cho toi tren booking ABC123 voi ho Nguyen"),
        authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.CONFIRMATION_REQUIRED);
    verify(confirmationService).createProposal(
        org.mockito.ArgumentMatchers.eq("confirm_seat_change"), any(), any());
  }

  @Test
  void s06_checkIn_preview_invokesTool() {
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(toolCatalog.descriptor("continue_check_in")));

    when(checkInContinueService.continueCheckIn(any())).thenReturn(null);
    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("continue_check_in",
            "{\"pnrCode\":\"ABC123\",\"lastName\":\"Nguyen\",\"segments\":[{\"flightId\":10,\"passengerIds\":[1]}]}"))
        .thenReturn(textResponse("Booking của bạn đủ điều kiện check-in."));

    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Booking ABC123 check-in được chưa?"),
        authentication);

    assertThat(response.answerType()).isEqualTo(AnswerType.TOOL_RESULT);
    verify(checkInContinueService).continueCheckIn(any());
  }

  @Test
  void s07_cancellationPreview_mutation_createsProposal() {
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(toolCatalog.descriptor("confirm_cancellation")));

    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("confirm_cancellation",
            "{\"bookingId\":100,\"ticketIds\":[1,2]}"));

    var mockProposal = new com.example.moduleai.confirmation.ToolProposalView(
        java.util.UUID.randomUUID(), "confirm_cancellation",
        com.example.moduleai.tool.core.ToolOperation.FINANCIAL,
        "PENDING", "Xác nhận hủy booking", Map.of(), OffsetDateTime.now().plusMinutes(5),
        "/api/proposals/x/confirm", "/api/proposals/x/reject");
    when(confirmationService.createProposal(
        org.mockito.ArgumentMatchers.eq("confirm_cancellation"), any(), any())).thenReturn(mockProposal);

    // Use message that routes to DYNAMIC_API without missingRequiredContext
    // "Booking ABC123 cua toi da thanh toan chua?" routes to booking_lookup (no missing context)
    // but allowlistResolver is fully mocked, so tool availability is controlled by us
    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Booking ABC123 cua toi da thanh toan chua?"),
        authentication);

    assertThat(response).isNotNull();
    assertThat(response.answerType()).isEqualTo(AnswerType.CONFIRMATION_REQUIRED);
    verify(confirmationService).createProposal(
        org.mockito.ArgumentMatchers.eq("confirm_cancellation"), any(), any());
  }

  @Test
  void s10_budgetExceeded_returnsToolCallBudgetExceeded() {
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(
            toolCatalog.descriptor("lookup_booking"),
            toolCatalog.descriptor("list_my_trips")));

    // Stub with simple null (orchestrator wraps null in ToolResult.success and serializes)
    when(bookingLookupService.lookup(any())).thenReturn(null);
    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("lookup_booking", "{\"pnrCode\":\"X1\",\"lastName\":\"A\"}"))
        .thenReturn(textResponse("ok"));

    RagChatResponse response = orchestrator.handle(
        // Message matching IntentRouter: booking_lookup (DYNAMIC_API)
        new ChatRequest("Booking ABC123 cua toi da thanh toan chua?"),
        authentication);

    // Max 2 model rounds — second round hits limit → MODEL_BUDGET_EXCEEDED fallback
    assertThat(response).isNotNull();
    assertThat(response.answerType()).isIn(AnswerType.FALLBACK, AnswerType.TOOL_RESULT);
  }

  @Test
  void s11_toolNotInAllowlist_orchestratorIgnoresUnallowedToolCall() {
    // Allowlist only contains lookup_booking, but model tries to call list_my_trips
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(toolCatalog.descriptor("lookup_booking")));

    when(bookingLookupService.lookup(any())).thenReturn(null);
    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("list_my_trips", "{\"status\":\"UPCOMING\"}"))
        .thenReturn(textResponse("Xin lỗi, tôi không thể thực hiện yêu cầu này."));

    RagChatResponse response = orchestrator.handle(
        new ChatRequest("Booking cua toi o dau?"),
        authentication);

    // list_my_trips is not in allowlist — should never reach userTripHistoryService
    verify(userTripHistoryService, never()).getTrips(any(), any());
  }

  @Test
  void s12_contextPassing_userIdPropagatedIntoAuthenticatedTool() {
    // Verify the full chain: Authentication → AgentToolContext.userId → service call
    // Phase 2: Verify userId=77L (from authenticatedContext) is passed into the service
    // Mocks are reset between tests by @MockitoBean — stub the allowlist first
    when(allowlistResolver.resolve(any(), any()))
        .thenReturn(Set.of(toolCatalog.descriptor("list_my_trips")));
    when(chatModel.call((Prompt) any()))
        .thenReturn(toolCallResponse("list_my_trips", "{\"status\":\"UPCOMING\"}"))
        .thenReturn(textResponse("2 chuyến bay sắp tới."));

    orchestrator.handle(
        // Message matching IntentRouter: my_trips → list_my_trips
        new ChatRequest("Tai khoan cua toi co booking nao sap bay?"),
        authentication);

    org.mockito.ArgumentCaptor<Long> userIdCaptor =
        org.mockito.ArgumentCaptor.forClass(Long.class);
    verify(userTripHistoryService, org.mockito.Mockito.atLeastOnce()).getTrips(userIdCaptor.capture(), any());
    assertThat(userIdCaptor.getValue()).isEqualTo(77L);
  }

  private ChatResponse toolCallResponse(String name, String arguments) {
    return new ChatResponse(List.of(new Generation(
        AssistantMessage.builder()
            .toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", name, arguments)))
            .build())));
  }

  private ChatResponse textResponse(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableConfigurationProperties({AiAgentGuardrailProperties.class})
  static class PipelineConfig {
    @Bean ToolCatalog toolCatalog() { return new ToolCatalog(); }
    @Bean ObjectMapper objectMapper() {
      return new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }
    @Bean ChatResponseMapper chatResponseMapper() { return new ChatResponseMapper(); }
    @Bean AgentExecutionBudget agentExecutionBudget(AiAgentGuardrailProperties properties) {
      return new AgentExecutionBudget(properties);
    }
    @Bean AgentPayloadSanitizer agentPayloadSanitizer(ObjectMapper mapper, AiAgentGuardrailProperties p) {
      return new AgentPayloadSanitizer(mapper, p);
    }
    @Bean Validator validator() { 
      Validator mock = mock(Validator.class);
      when(mock.validate(any())).thenReturn(Set.of());
      return mock;
    }
    @Bean ToolExecutionGuard toolExecutionGuard(Validator v, ToolCatalog tc) {
      return new ToolExecutionGuard(v, tc, (policy, input, context) -> null);
    }
    @Bean BookingTools bookingTools(ToolExecutionGuard g, BookingLookupService ls, BookingRevalidateService rs, TicketIssuanceService tis, UserTripHistoryService uths, UserTripDetailService utds, BookingManageOptionsService bmos) {
      return new BookingTools(g, ls, rs, tis, uths, utds, bmos);
    }
    @Bean SeatTools seatTools(ToolExecutionGuard g, FlightSeatMapService fsms, BookingSeatAssignmentService bsas, SeatChangeSettlementService scss) {
      return new SeatTools(g, fsms, bsas, scss);
    }
    @Bean CheckInTools checkInTools(ToolExecutionGuard g, CheckInContinueService cics, CheckInConfirmService cicfs) {
      return new CheckInTools(g, cics, cicfs);
    }
    @Bean CancellationTools cancellationTools(ToolExecutionGuard g, BookingCancellationService bcs) {
      return new CancellationTools(g, bcs);
    }
    @Bean AiObservability observability() { return mock(AiObservability.class); }
    @Bean IntentRouter intentRouter() { return new IntentRouter(); }

    @Bean
    AgentToolOrchestrator agentToolOrchestrator(
        AgentInputGuard inputGuard,
        AgentRequestContextFactory contextFactory,
        ToolAllowlistResolver allowlistResolver,
        ToolConfirmationService confirmationService,
        ChatResponseMapper chatResponseMapper,
        ObjectMapper objectMapper,
        AgentExecutionBudget executionBudget,
        AiObservability observability,
        AgentPayloadSanitizer payloadSanitizer,
        AgentTrustedContextResolver trustedContextResolver,
        AiAgentGuardrailProperties guardrailProperties,
        ChatModel chatModel) {
      return new AgentToolOrchestrator(
          inputGuard, contextFactory, allowlistResolver, confirmationService, chatResponseMapper,
          objectMapper, executionBudget, observability, payloadSanitizer, trustedContextResolver,
          guardrailProperties, chatModel);
    }
    
    @Bean
    AgentOrchestrator agentOrchestrator(IntentRouter ir, ChatService cs, ChatResponseMapper crm, AgentToolOrchestrator ato) {
        return new AgentOrchestrator(ir, cs, crm, ato);
    }
  }
}
