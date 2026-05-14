package com.example.appbootstrap.controller.payment;

import com.example.appbootstrap.controller.payment.dto.CancelPaymentRequest;
import com.example.appbootstrap.controller.payment.dto.CreatePaymentRequest;
import com.example.appbootstrap.controller.payment.dto.CreatePaymentResponse;
import com.example.appbootstrap.controller.payment.dto.PaymentMethodsResponse;
import com.example.appbootstrap.controller.payment.dto.PaymentStatusResponse;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.modulepayment.application.CreatePaymentCommand;
import com.example.modulepayment.application.CreatePaymentResult;
import com.example.modulepayment.application.PaymentDetailsView;
import com.example.modulepayment.application.PaymentMethodView;
import com.example.modulepayment.application.PaymentResultService;
import com.example.modulepayment.application.PaymentResultStatusView;
import com.example.modulepayment.application.PaymentResultSummaryView;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.PaymentStatusView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Payments", description = "Payment and transaction endpoints")
@RequestMapping("/api")
public class PaymentsController {

  private final PaymentService paymentService;
  private final PaymentResultService paymentResultService;

  public PaymentsController(
      PaymentService paymentService, PaymentResultService paymentResultService) {
    this.paymentService = paymentService;
    this.paymentResultService = paymentResultService;
  }

  @GetMapping("/payment-methods")
  @Operation(
      summary = "Get supported payment methods",
      description =
          "Returns payment methods available for the booking (public for checkout; still enforces booking payable rules).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Payment methods returned successfully",
        content = @Content(schema = @Schema(implementation = PaymentMethodsResponse.class))),
    @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content),
    @ApiResponse(responseCode = "409", description = "Booking not payable", content = @Content),
    @ApiResponse(responseCode = "410", description = "Booking expired", content = @Content)
  })
  PaymentMethodsResponse getPaymentMethods(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @RequestParam @NotNull(message = "bookingId is required") Long bookingId,
      @Parameter(description = "If true, omit payment methods that are not yet supported")
          @RequestParam(defaultValue = "false")
          boolean onlySupported) {
    List<PaymentMethodView> groups =
        principal == null
            ? paymentService.getPaymentMethodsPublic(bookingId, onlySupported)
            : paymentService.getPaymentMethods(bookingId, principal.getUserId(), onlySupported);
    return new PaymentMethodsResponse(
        groups.stream()
            .map(
                group ->
                    new PaymentMethodsResponse.PaymentMethodGroupResponse(
                        group.type(),
                        group.label(),
                        group.methods().stream()
                            .map(
                                method ->
                                    new PaymentMethodsResponse.PaymentMethodItemResponse(
                                        method.code(),
                                        method.label(),
                                        method.provider(),
                                        method.icon(),
                                        method.supported(),
                                        method.meta() == null
                                            ? null
                                            : new PaymentMethodsResponse.PaymentMethodMetaResponse(
                                                method.meta().holdDurationMinutes(),
                                                method.meta().fee())))
                            .toList()))
            .toList());
  }

  @GetMapping("/payments/{paymentId}")
  @Operation(
      summary = "Get payment status",
      description =
          "Returns the current payment record for polling after PayOS redirect (webhook may lag).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Payment status returned",
        content = @Content(schema = @Schema(implementation = PaymentStatusResponse.class))),
    @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
  })
  PaymentStatusResponse getPaymentStatus(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable @Positive(message = "paymentId must be positive") Long paymentId) {
    PaymentStatusView view =
        principal == null
            ? paymentService.getPaymentPublic(paymentId)
            : paymentService.getPaymentForUser(paymentId, principal.getUserId());
    return new PaymentStatusResponse(
        view.id(),
        view.bookingId(),
        view.amount(),
        view.currency(),
        view.method(),
        view.status(),
        view.paidAt(),
        view.expireAt(),
        view.transactionId(),
        view.qrData(),
        view.qrImageUrl(),
        view.paymentUrl(),
        toStatusDetails(view.details()));
  }

  @GetMapping("/payments/{paymentId}/result-status")
  @Operation(
      summary = "Get payment result page status",
      description = "Verifies the backend payment outcome for success/error result pages.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Payment result status returned"),
    @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
  })
  PaymentResultStatusView getPaymentResultStatus(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable @Positive(message = "paymentId must be positive") Long paymentId) {
    return principal == null
        ? paymentResultService.getResultStatusPublic(paymentId)
        : paymentResultService.getResultStatusForUser(paymentId, principal.getUserId());
  }

  @GetMapping("/payments/{paymentId}/result-summary")
  @Operation(
      summary = "Get payment success summary",
      description =
          "Returns the full backend-backed success page summary once payment is successful.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Payment success summary returned"),
    @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Payment not completed or not successful",
        content = @Content)
  })
  PaymentResultSummaryView getPaymentResultSummary(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable @Positive(message = "paymentId must be positive") Long paymentId) {
    return principal == null
        ? paymentResultService.getResultSummaryPublic(paymentId)
        : paymentResultService.getResultSummaryForUser(paymentId, principal.getUserId());
  }

  @PostMapping("/payments")
  @Operation(
      summary = "Create payment link",
      description = "Creates a pending payment for a booking")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Payment created successfully",
        content = @Content(schema = @Schema(implementation = CreatePaymentResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content),
    @ApiResponse(responseCode = "409", description = "Booking not payable", content = @Content),
    @ApiResponse(responseCode = "410", description = "Booking expired", content = @Content),
    @ApiResponse(responseCode = "502", description = "Provider error", content = @Content)
  })
  CreatePaymentResponse createPayment(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody CreatePaymentRequest request) {
    CreatePaymentResult result =
        principal == null
            ? paymentService.createPaymentPublic(
                new CreatePaymentCommand(request.bookingId(), null, request.method()))
            : paymentService.createPayment(
                new CreatePaymentCommand(
                    request.bookingId(), principal.getUserId(), request.method()));
    return new CreatePaymentResponse(
        new CreatePaymentResponse.PaymentResponse(
            result.payment().id(),
            result.payment().bookingId(),
            result.payment().amount(),
            result.payment().currency(),
            result.payment().method(),
            result.payment().status(),
            result.payment().expireAt(),
            result.payment().transactionId(),
            result.payment().qrData(),
            toCreateDetails(result.payment().details())),
        new CreatePaymentResponse.PaymentActionResponse(
            result.action().type(),
            result.action().qrData(),
            result.action().qrImageUrl(),
            result.action().paymentUrl()));
  }

  @PostMapping("/payments/{paymentId}/cancel")
  @Operation(
      summary = "Cancel a pending payment",
      description = "Cancels the PayOS payment link for a pending payment")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Payment cancelled",
        content = @Content(schema = @Schema(implementation = PaymentStatusResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content),
    @ApiResponse(responseCode = "409", description = "Payment not cancellable", content = @Content),
    @ApiResponse(responseCode = "502", description = "Provider error", content = @Content)
  })
  PaymentStatusResponse cancelPayment(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable @Positive(message = "paymentId must be positive") Long paymentId,
      @RequestBody(required = false) CancelPaymentRequest request) {
    PaymentStatusView view =
        principal == null
            ? paymentService.cancelPaymentPublic(
                paymentId, request == null ? null : request.reason())
            : paymentService.cancelPaymentForUser(
                paymentId, principal.getUserId(), request == null ? null : request.reason());
    return new PaymentStatusResponse(
        view.id(),
        view.bookingId(),
        view.amount(),
        view.currency(),
        view.method(),
        view.status(),
        view.paidAt(),
        view.expireAt(),
        view.transactionId(),
        view.qrData(),
        view.qrImageUrl(),
        view.paymentUrl(),
        toStatusDetails(view.details()));
  }

  private CreatePaymentResponse.PaymentDetailsResponse toCreateDetails(PaymentDetailsView details) {
    if (details == null) {
      return null;
    }
    return new CreatePaymentResponse.PaymentDetailsResponse(
        toCreateFlight(details.flight()), toCreatePrice(details.price()));
  }

  private PaymentStatusResponse.PaymentDetailsResponse toStatusDetails(PaymentDetailsView details) {
    if (details == null) {
      return null;
    }
    return new PaymentStatusResponse.PaymentDetailsResponse(
        toStatusFlight(details.flight()), toStatusPrice(details.price()));
  }

  private CreatePaymentResponse.FlightResponse toCreateFlight(
      PaymentDetailsView.FlightView flight) {
    if (flight == null) {
      return null;
    }
    return new CreatePaymentResponse.FlightResponse(
        flight.flightNumber(),
        flight.flightName(),
        flight.airline(),
        flight.departureCode(),
        flight.departureCity(),
        flight.departureName(),
        flight.arrivalCode(),
        flight.arrivalCity(),
        flight.arrivalName(),
        flight.flightDate(),
        flight.departureTime(),
        flight.arrivalTime());
  }

  private PaymentStatusResponse.FlightResponse toStatusFlight(
      PaymentDetailsView.FlightView flight) {
    if (flight == null) {
      return null;
    }
    return new PaymentStatusResponse.FlightResponse(
        flight.flightNumber(),
        flight.flightName(),
        flight.airline(),
        flight.departureCode(),
        flight.departureCity(),
        flight.departureName(),
        flight.arrivalCode(),
        flight.arrivalCity(),
        flight.arrivalName(),
        flight.flightDate(),
        flight.departureTime(),
        flight.arrivalTime());
  }

  private CreatePaymentResponse.PriceBreakdownResponse toCreatePrice(
      PaymentDetailsView.PriceBreakdownView price) {
    if (price == null) {
      return null;
    }
    return new CreatePaymentResponse.PriceBreakdownResponse(
        price.baseFare(),
        price.discount(),
        price.tax(),
        price.serviceFee(),
        price.seatFee(),
        price.total(),
        price.currency());
  }

  private PaymentStatusResponse.PriceBreakdownResponse toStatusPrice(
      PaymentDetailsView.PriceBreakdownView price) {
    if (price == null) {
      return null;
    }
    return new PaymentStatusResponse.PriceBreakdownResponse(
        price.baseFare(),
        price.discount(),
        price.tax(),
        price.serviceFee(),
        price.seatFee(),
        price.total(),
        price.currency());
  }
}
