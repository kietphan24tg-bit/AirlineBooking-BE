package com.example.appbootstrap.controller.payment;

import com.example.appbootstrap.controller.payment.dto.CreatePaymentRequest;
import com.example.appbootstrap.controller.payment.dto.CreatePaymentResponse;
import com.example.appbootstrap.controller.payment.dto.PaymentMethodsResponse;
import com.example.appbootstrap.controller.payment.dto.PaymentStatusResponse;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.modulepayment.application.CreatePaymentCommand;
import com.example.modulepayment.application.CreatePaymentResult;
import com.example.modulepayment.application.PaymentMethodView;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.PaymentStatusView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

  public PaymentsController(PaymentService paymentService) {
    this.paymentService = paymentService;
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
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Get payment status",
      description =
          "Returns the current payment record for polling after PayOS redirect (webhook may lag).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Payment status returned",
        content = @Content(schema = @Schema(implementation = PaymentStatusResponse.class))),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
  })
  PaymentStatusResponse getPaymentStatus(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable @Positive(message = "paymentId must be positive") Long paymentId) {
    PaymentStatusView view = paymentService.getPaymentForUser(paymentId, principal.getUserId());
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
        view.paymentUrl());
  }

  @PostMapping("/payments")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Create payment link",
      description = "Creates a pending payment for the authenticated user's booking")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Payment created successfully",
        content = @Content(schema = @Schema(implementation = CreatePaymentResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
    @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content),
    @ApiResponse(responseCode = "409", description = "Booking not payable", content = @Content),
    @ApiResponse(responseCode = "410", description = "Booking expired", content = @Content),
    @ApiResponse(responseCode = "502", description = "Provider error", content = @Content)
  })
  CreatePaymentResponse createPayment(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody CreatePaymentRequest request) {
    CreatePaymentResult result =
        paymentService.createPayment(
            new CreatePaymentCommand(request.bookingId(), principal.getUserId(), request.method()));
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
            result.payment().qrData()),
        new CreatePaymentResponse.PaymentActionResponse(
            result.action().type(),
            result.action().qrData(),
            result.action().qrImageUrl(),
            result.action().paymentUrl()));
  }
}
