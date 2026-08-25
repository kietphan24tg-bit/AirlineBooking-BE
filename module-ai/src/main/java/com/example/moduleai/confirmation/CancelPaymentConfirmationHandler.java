package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.PaymentTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.PaymentStatusView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

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
