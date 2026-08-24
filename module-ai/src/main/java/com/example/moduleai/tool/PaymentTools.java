package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.modulepayment.application.CreatePaymentCommand;
import com.example.modulepayment.application.CreatePaymentResult;
import com.example.modulepayment.application.FlightChangePaymentService;
import com.example.modulepayment.application.PaymentMethodView;
import com.example.modulepayment.application.PaymentResultService;
import com.example.modulepayment.application.PaymentResultStatusView;
import com.example.modulepayment.application.PaymentResultSummaryView;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.PaymentStatusView;
import com.example.modulepayment.application.SeatChangePaymentService;
import com.example.modulepayment.application.ServiceChangePaymentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class PaymentTools extends AbstractAgentTool {

  private final PaymentService paymentService;
  private final PaymentResultService paymentResultService;
  private final SeatChangePaymentService seatChangePaymentService;
  private final ServiceChangePaymentService serviceChangePaymentService;
  private final FlightChangePaymentService flightChangePaymentService;

  public PaymentTools(
      ToolExecutionGuard guard,
      PaymentService paymentService,
      PaymentResultService paymentResultService,
      SeatChangePaymentService seatChangePaymentService,
      ServiceChangePaymentService serviceChangePaymentService,
      FlightChangePaymentService flightChangePaymentService) {
    super(guard);
    this.paymentService = paymentService;
    this.paymentResultService = paymentResultService;
    this.seatChangePaymentService = seatChangePaymentService;
    this.serviceChangePaymentService = serviceChangePaymentService;
    this.flightChangePaymentService = flightChangePaymentService;
  }

  @Tool(description = "List supported payment methods for a booking.")
  @AgentToolPolicy(
      tool = "list_payment_methods",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<List<PaymentMethodView>> list_payment_methods(
      ListPaymentMethodsInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "list_payment_methods",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        context,
        () ->
            context.userId() == null
                ? paymentService.getPaymentMethodsPublic(input.bookingId(), input.onlySupported())
                : paymentService.getPaymentMethods(
                    input.bookingId(), context.userId(), input.onlySupported()));
  }

  @Tool(description = "Get the current payment detail and status for a payment id.")
  @AgentToolPolicy(
      tool = "get_payment_detail",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<PaymentStatusView> get_payment_detail(
      PaymentIdInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_payment_detail",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        context,
        () ->
            context.userId() == null
                ? paymentService.getPaymentPublic(input.paymentId())
                : paymentService.getPaymentForUser(input.paymentId(), context.userId()));
  }

  @Tool(description = "Get the backend-verified status for a payment result page.")
  @AgentToolPolicy(
      tool = "get_payment_result_status",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<PaymentResultStatusView> get_payment_result_status(
      PaymentIdInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_payment_result_status",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        context,
        () ->
            context.userId() == null
                ? paymentResultService.getResultStatusPublic(input.paymentId())
                : paymentResultService.getResultStatusForUser(input.paymentId(), context.userId()));
  }

  @Tool(description = "Get the payment success or pending summary for a payment id.")
  @AgentToolPolicy(
      tool = "get_payment_result_summary",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<PaymentResultSummaryView> get_payment_result_summary(
      PaymentIdInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_payment_result_summary",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        context,
        () ->
            context.userId() == null
                ? paymentResultService.getResultSummaryPublic(input.paymentId())
                : paymentResultService.getResultSummaryForUser(
                    input.paymentId(), context.userId()));
  }

  @Tool(description = "Create a booking payment link.")
  @AgentToolPolicy(
      tool = "create_booking_payment",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<CreatePaymentResult> create_booking_payment(
      CreateBookingPaymentInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "create_booking_payment",
        ToolOperation.FINANCIAL,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            paymentService.createPayment(
                new CreatePaymentCommand(
                    input.bookingId(),
                    context.userId(),
                    input.method(),
                    context.idempotencyKey())));
  }

  @Tool(description = "Create a payment link for an additional seat change amount.")
  @AgentToolPolicy(
      tool = "create_seat_change_payment",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<CreatePaymentResult> create_seat_change_payment(
      CreateSeatChangePaymentInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "create_seat_change_payment",
        ToolOperation.FINANCIAL,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            seatChangePaymentService.createPayment(
                input.bookingId(),
                input.seatChangeSettlementId(),
                context.userId(),
                input.method(),
                context.idempotencyKey()));
  }

  @Tool(description = "Create a payment link for an additional service change amount.")
  @AgentToolPolicy(
      tool = "create_service_change_payment",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<CreatePaymentResult> create_service_change_payment(
      CreateServiceChangePaymentInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "create_service_change_payment",
        ToolOperation.FINANCIAL,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            serviceChangePaymentService.createPayment(
                input.bookingId(),
                input.serviceChangeSettlementId(),
                context.userId(),
                input.method(),
                context.idempotencyKey()));
  }

  @Tool(description = "Create a payment link for an additional flight change amount.")
  @AgentToolPolicy(
      tool = "create_flight_change_payment",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<CreatePaymentResult> create_flight_change_payment(
      CreateFlightChangePaymentInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "create_flight_change_payment",
        ToolOperation.FINANCIAL,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            flightChangePaymentService.createPayment(
                input.bookingId(),
                input.flightChangeSettlementId(),
                context.userId(),
                input.method(),
                context.idempotencyKey()));
  }

  @Tool(description = "Cancel a pending payment link.")
  @AgentToolPolicy(
      tool = "cancel_payment",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<PaymentStatusView> cancel_payment(
      CancelPaymentInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "cancel_payment",
        ToolOperation.FINANCIAL,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            paymentService.cancelPaymentForUser(
                input.paymentId(), context.userId(), input.reason()));
  }

  public record ListPaymentMethodsInput(@NotNull Long bookingId, boolean onlySupported) {}

  public record PaymentIdInput(@NotNull @Positive Long paymentId) {}

  public record CreateBookingPaymentInput(@NotNull Long bookingId, @NotBlank String method) {}

  public record CreateSeatChangePaymentInput(
      @NotNull Long bookingId, @NotNull Long seatChangeSettlementId, @NotBlank String method) {}

  public record CreateServiceChangePaymentInput(
      @NotNull Long bookingId, @NotNull Long serviceChangeSettlementId, @NotBlank String method) {}

  public record CreateFlightChangePaymentInput(
      @NotNull Long bookingId, @NotNull Long flightChangeSettlementId, @NotBlank String method) {}

  public record CancelPaymentInput(@NotNull @Positive Long paymentId, String reason) {}
}
