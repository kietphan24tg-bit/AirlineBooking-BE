package com.example.moduleai.confirmation;

import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class BookingPaymentExecutionReconciler implements ToolExecutionReconciler {

  private final PaymentRepository paymentRepository;
  private final ObjectMapper objectMapper;

  BookingPaymentExecutionReconciler(
      PaymentRepository paymentRepository, ObjectMapper objectMapper) {
    this.paymentRepository = paymentRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "create_booking_payment";
  }

  @Override
  public ToolExecutionReconciliation reconcile(ToolProposalEntity proposal) {
    return paymentRepository
        .findByRequestKeyWithBooking(proposal.getIdempotencyKey())
        .map(payment -> mapPayment(payment, true))
        .orElseGet(ToolExecutionReconciliation::unresolved);
  }

  private ToolExecutionReconciliation mapPayment(PaymentEntity payment, boolean created) {
    Map<String, Object> payload = paymentPayload(payment);
    String status = normalize(payment.getPaymentStatus());
    if (created
        && (status.equals("PENDING") || status.equals("SUSPENDED") || status.equals("SUCCESS"))) {
      return ToolExecutionReconciliation.executed(payload);
    }
    if (status.equals("FAILED") || status.equals("CANCELLED") || status.equals("EXPIRED")) {
      return ToolExecutionReconciliation.failed(payload);
    }
    return ToolExecutionReconciliation.unresolved();
  }

  private Map<String, Object> paymentPayload(PaymentEntity payment) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("paymentId", payment.getId());
    payload.put("bookingId", payment.getBooking().getId());
    payload.put("amount", payment.getAmount());
    payload.put("currency", payment.getCurrency());
    payload.put("method", payment.getPaymentMethod());
    payload.put("status", payment.getPaymentStatus());
    payload.put("expireAt", payment.getSessionExpiresAt());
    return payload;
  }

  private boolean sameMethod(String left, String right) {
    return normalizePaymentMethod(left).equals(normalizePaymentMethod(right));
  }

  private String normalizePaymentMethod(String method) {
    String normalized = normalize(method);
    if ("PAYOS_QR".equals(normalized)) {
      return "PAYOS";
    }
    if ("STRIPE".equals(normalized)) {
      return "STRIPE_CARD";
    }
    return normalized;
  }

  private <T> T deserialize(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to deserialize proposal arguments", exception);
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
