package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.PaymentTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.FlightChangeSettlementService;
import com.example.modulepayment.entity.FlightChangeSettlementEntity;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

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
