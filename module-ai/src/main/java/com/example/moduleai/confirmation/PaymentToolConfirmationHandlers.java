package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.PaymentTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.FlightChangeSettlementService;
import com.example.modulepayment.application.PaymentMethodView;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.PaymentStatusView;
import com.example.modulepayment.application.SeatChangeSettlementService;
import com.example.modulepayment.application.ServiceChangeSettlementService;
import com.example.modulepayment.entity.FlightChangeSettlementEntity;
import com.example.modulepayment.entity.SeatChangeSettlementEntity;
import com.example.modulepayment.entity.ServiceChangeSettlementEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class BookingPaymentConfirmationHandler
    implements ToolConfirmationPresenter<PaymentTools.CreateBookingPaymentInput>,
        ToolConfirmationRevalidator<PaymentTools.CreateBookingPaymentInput> {

  private final PaymentService paymentService;

  BookingPaymentConfirmationHandler(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @Override
  public String toolName() {
    return "create_booking_payment";
  }

  @Override
  public Object present(PaymentTools.CreateBookingPaymentInput input, AgentToolContext context) {
    List<PaymentMethodView> methods =
        paymentService.getPaymentMethods(input.bookingId(), context.userId(), true);
    String normalizedMethod = normalizeBookingPaymentMethod(input.method());
    assertSupportedMethod(methods, normalizedMethod);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("summary", "Create booking payment for booking " + input.bookingId());
    snapshot.put("bookingId", input.bookingId());
    snapshot.put("method", normalizedMethod);
    snapshot.put("supportedMethods", methods);
    return snapshot;
  }

  @Override
  public void revalidate(
      PaymentTools.CreateBookingPaymentInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    Map<String, Object> fresh = map(present(input, context));
    Map<String, Object> original = map(originalSnapshot);
    if (!Objects.equals(original.get("method"), fresh.get("method"))) {
      throw stale("BOOKING_PAYMENT_PREVIEW_STALE");
    }
  }

  private String normalizeBookingPaymentMethod(String method) {
    String normalized = normalizeMethod(method);
    if ("PAYOS_QR".equals(normalized)) {
      return "PAYOS";
    }
    if ("STRIPE".equals(normalized)) {
      return "STRIPE_CARD";
    }
    return normalized;
  }

  private void assertSupportedMethod(List<PaymentMethodView> groups, String method) {
    boolean supported =
        groups.stream()
            .flatMap(group -> group.methods().stream())
            .anyMatch(
                item ->
                    method.equalsIgnoreCase(normalizeBookingPaymentMethod(item.code()))
                        && item.supported());
    if (!supported) {
      throw new AppException("UNSUPPORTED_PAYMENT_METHOD", "Payment method is not supported", 400);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private AppException stale(String code) {
    return new AppException(
        code, "Payment confirmation data is out of date. Please review again.", 409);
  }

  private String normalizeMethod(String method) {
    return method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
  }
}

@Component
class SeatChangePaymentConfirmationHandler
    implements ToolConfirmationPresenter<PaymentTools.CreateSeatChangePaymentInput>,
        ToolConfirmationRevalidator<PaymentTools.CreateSeatChangePaymentInput> {

  private final SeatChangeSettlementService settlementService;

  SeatChangePaymentConfirmationHandler(SeatChangeSettlementService settlementService) {
    this.settlementService = settlementService;
  }

  @Override
  public String toolName() {
    return "create_seat_change_payment";
  }

  @Override
  public Object present(PaymentTools.CreateSeatChangePaymentInput input, AgentToolContext context) {
    assertSettlementPaymentMethod(input.method());
    SeatChangeSettlementEntity settlement =
        settlementService.requireSettlementForPayment(
            input.bookingId(), input.seatChangeSettlementId(), context.userId());
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("summary", "Create seat change payment for booking " + input.bookingId());
    snapshot.put("bookingId", input.bookingId());
    snapshot.put("settlementId", settlement.getId());
    snapshot.put("method", normalizeSettlementPaymentMethod(input.method()));
    snapshot.put("amount", settlement.getNetAmount());
    snapshot.put("currency", settlement.getCurrency());
    snapshot.put("settlementStatus", settlement.getStatus());
    return snapshot;
  }

  @Override
  public void revalidate(
      PaymentTools.CreateSeatChangePaymentInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    compareSnapshot("SEAT_CHANGE_PAYMENT_PREVIEW_STALE", originalSnapshot, present(input, context));
  }

  private void assertSettlementPaymentMethod(String method) {
    String normalized = normalizeSettlementPaymentMethod(method);
    if (!"PAYOS".equals(normalized) && !"STRIPE_CARD".equals(normalized)) {
      throw new AppException("UNSUPPORTED_PAYMENT_METHOD", "Payment method is not supported", 400);
    }
  }

  private String normalizeSettlementPaymentMethod(String method) {
    String normalized = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
    if ("PAYOS_QR".equals(normalized)) {
      return "PAYOS";
    }
    if ("STRIPE".equals(normalized)) {
      return "STRIPE_CARD";
    }
    return normalized;
  }

  private void compareSnapshot(String code, Object originalSnapshot, Object freshSnapshot) {
    Map<String, Object> original = map(originalSnapshot);
    Map<String, Object> fresh = map(freshSnapshot);
    if (!sameValue(original.get("amount"), fresh.get("amount"))
        || !sameValue(original.get("currency"), fresh.get("currency"))
        || !sameValue(original.get("settlementStatus"), fresh.get("settlementStatus"))
        || !sameValue(original.get("method"), fresh.get("method"))) {
      throw new AppException(
          code, "Payment confirmation data is out of date. Please review again.", 409);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private boolean sameValue(Object left, Object right) {
    return Objects.equals(String.valueOf(left), String.valueOf(right));
  }
}

@Component
class ServiceChangePaymentConfirmationHandler
    implements ToolConfirmationPresenter<PaymentTools.CreateServiceChangePaymentInput>,
        ToolConfirmationRevalidator<PaymentTools.CreateServiceChangePaymentInput> {

  private final ServiceChangeSettlementService settlementService;

  ServiceChangePaymentConfirmationHandler(ServiceChangeSettlementService settlementService) {
    this.settlementService = settlementService;
  }

  @Override
  public String toolName() {
    return "create_service_change_payment";
  }

  @Override
  public Object present(
      PaymentTools.CreateServiceChangePaymentInput input, AgentToolContext context) {
    assertSettlementPaymentMethod(input.method());
    ServiceChangeSettlementEntity settlement =
        settlementService.requireSettlementForPayment(
            input.bookingId(), input.serviceChangeSettlementId(), context.userId());
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("summary", "Create service change payment for booking " + input.bookingId());
    snapshot.put("bookingId", input.bookingId());
    snapshot.put("settlementId", settlement.getId());
    snapshot.put("method", normalizeSettlementPaymentMethod(input.method()));
    snapshot.put("amount", settlement.getNetAmount());
    snapshot.put("currency", settlement.getCurrency());
    snapshot.put("settlementStatus", settlement.getStatus());
    return snapshot;
  }

  @Override
  public void revalidate(
      PaymentTools.CreateServiceChangePaymentInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    compareSnapshot(
        "SERVICE_CHANGE_PAYMENT_PREVIEW_STALE", originalSnapshot, present(input, context));
  }

  private void assertSettlementPaymentMethod(String method) {
    String normalized = normalizeSettlementPaymentMethod(method);
    if (!"PAYOS".equals(normalized) && !"STRIPE_CARD".equals(normalized)) {
      throw new AppException("UNSUPPORTED_PAYMENT_METHOD", "Payment method is not supported", 400);
    }
  }

  private String normalizeSettlementPaymentMethod(String method) {
    String normalized = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
    if ("PAYOS_QR".equals(normalized)) {
      return "PAYOS";
    }
    if ("STRIPE".equals(normalized)) {
      return "STRIPE_CARD";
    }
    return normalized;
  }

  private void compareSnapshot(String code, Object originalSnapshot, Object freshSnapshot) {
    Map<String, Object> original = map(originalSnapshot);
    Map<String, Object> fresh = map(freshSnapshot);
    if (!sameValue(original.get("amount"), fresh.get("amount"))
        || !sameValue(original.get("currency"), fresh.get("currency"))
        || !sameValue(original.get("settlementStatus"), fresh.get("settlementStatus"))
        || !sameValue(original.get("method"), fresh.get("method"))) {
      throw new AppException(
          code, "Payment confirmation data is out of date. Please review again.", 409);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private boolean sameValue(Object left, Object right) {
    return Objects.equals(String.valueOf(left), String.valueOf(right));
  }
}

@Component
class FlightChangePaymentConfirmationHandler
    implements ToolConfirmationPresenter<PaymentTools.CreateFlightChangePaymentInput>,
        ToolConfirmationRevalidator<PaymentTools.CreateFlightChangePaymentInput> {

  private final FlightChangeSettlementService settlementService;

  FlightChangePaymentConfirmationHandler(FlightChangeSettlementService settlementService) {
    this.settlementService = settlementService;
  }

  @Override
  public String toolName() {
    return "create_flight_change_payment";
  }

  @Override
  public Object present(
      PaymentTools.CreateFlightChangePaymentInput input, AgentToolContext context) {
    assertSettlementPaymentMethod(input.method());
    FlightChangeSettlementEntity settlement =
        settlementService.requireSettlementForPayment(
            input.bookingId(), input.flightChangeSettlementId(), context.userId());
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("summary", "Create flight change payment for booking " + input.bookingId());
    snapshot.put("bookingId", input.bookingId());
    snapshot.put("settlementId", settlement.getId());
    snapshot.put("method", normalizeSettlementPaymentMethod(input.method()));
    snapshot.put("amount", settlement.getNetAmount());
    snapshot.put("currency", settlement.getCurrency());
    snapshot.put("settlementStatus", settlement.getStatus());
    return snapshot;
  }

  @Override
  public void revalidate(
      PaymentTools.CreateFlightChangePaymentInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    compareSnapshot(
        "FLIGHT_CHANGE_PAYMENT_PREVIEW_STALE", originalSnapshot, present(input, context));
  }

  private void assertSettlementPaymentMethod(String method) {
    String normalized = normalizeSettlementPaymentMethod(method);
    if (!"PAYOS".equals(normalized) && !"STRIPE_CARD".equals(normalized)) {
      throw new AppException("UNSUPPORTED_PAYMENT_METHOD", "Payment method is not supported", 400);
    }
  }

  private String normalizeSettlementPaymentMethod(String method) {
    String normalized = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
    if ("PAYOS_QR".equals(normalized)) {
      return "PAYOS";
    }
    if ("STRIPE".equals(normalized)) {
      return "STRIPE_CARD";
    }
    return normalized;
  }

  private void compareSnapshot(String code, Object originalSnapshot, Object freshSnapshot) {
    Map<String, Object> original = map(originalSnapshot);
    Map<String, Object> fresh = map(freshSnapshot);
    if (!sameValue(original.get("amount"), fresh.get("amount"))
        || !sameValue(original.get("currency"), fresh.get("currency"))
        || !sameValue(original.get("settlementStatus"), fresh.get("settlementStatus"))
        || !sameValue(original.get("method"), fresh.get("method"))) {
      throw new AppException(
          code, "Payment confirmation data is out of date. Please review again.", 409);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private boolean sameValue(Object left, Object right) {
    return Objects.equals(String.valueOf(left), String.valueOf(right));
  }
}

@Component
class CancelPaymentConfirmationHandler
    implements ToolConfirmationPresenter<PaymentTools.CancelPaymentInput>,
        ToolConfirmationRevalidator<PaymentTools.CancelPaymentInput> {

  private final PaymentService paymentService;

  CancelPaymentConfirmationHandler(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @Override
  public String toolName() {
    return "cancel_payment";
  }

  @Override
  public Object present(PaymentTools.CancelPaymentInput input, AgentToolContext context) {
    PaymentStatusView payment =
        paymentService.getPaymentForUser(input.paymentId(), context.userId());
    if (!"PENDING".equalsIgnoreCase(payment.status())) {
      throw new AppException(
          "PAYMENT_NOT_CANCELLABLE", "Only pending payments can be cancelled", 409);
    }
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("summary", "Cancel pending payment " + input.paymentId());
    snapshot.put("paymentId", input.paymentId());
    snapshot.put("bookingId", payment.bookingId());
    snapshot.put("amount", payment.amount());
    snapshot.put("currency", payment.currency());
    snapshot.put("method", payment.method());
    snapshot.put("status", payment.status());
    snapshot.put("expireAt", payment.expireAt());
    return snapshot;
  }

  @Override
  public void revalidate(
      PaymentTools.CancelPaymentInput input, Object originalSnapshot, AgentToolContext context) {
    Map<String, Object> original = map(originalSnapshot);
    Map<String, Object> fresh = map(present(input, context));
    if (!sameValue(original.get("status"), fresh.get("status"))
        || !sameValue(original.get("amount"), fresh.get("amount"))
        || !sameValue(original.get("currency"), fresh.get("currency"))) {
      throw new AppException(
          "PAYMENT_CANCELLATION_PREVIEW_STALE",
          "Payment cancellation data is out of date. Please review again.",
          409);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private boolean sameValue(Object left, Object right) {
    return Objects.equals(String.valueOf(left), String.valueOf(right));
  }
}
