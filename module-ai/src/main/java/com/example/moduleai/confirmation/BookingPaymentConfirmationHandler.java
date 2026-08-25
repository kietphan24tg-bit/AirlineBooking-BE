package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.PaymentTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.PaymentMethodView;
import com.example.modulepayment.application.PaymentService;
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
