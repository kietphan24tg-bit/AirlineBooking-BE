package com.example.moduleai.confirmation;

import com.example.moduleai.tool.PaymentTools;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.PaymentStatusView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class CancelPaymentExecutionReconciler implements ToolExecutionReconciler {

  private final PaymentService paymentService;
  private final ObjectMapper objectMapper;

  CancelPaymentExecutionReconciler(PaymentService paymentService, ObjectMapper objectMapper) {
    this.paymentService = paymentService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "cancel_payment";
  }

  @Override
  public ToolExecutionReconciliation reconcile(ToolProposalEntity proposal) {
    PaymentTools.CancelPaymentInput input =
        deserialize(proposal.getCanonicalArguments(), PaymentTools.CancelPaymentInput.class);
    PaymentStatusView payment =
        paymentService.getPaymentForUser(input.paymentId(), proposal.getUserId());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("paymentId", payment.id());
    payload.put("bookingId", payment.bookingId());
    payload.put("amount", payment.amount());
    payload.put("currency", payment.currency());
    payload.put("method", payment.method());
    payload.put("status", payment.status());
    String status =
        payment.status() == null ? "" : payment.status().trim().toUpperCase(Locale.ROOT);
    if ("CANCELLED".equals(status)) {
      return ToolExecutionReconciliation.executed(payload);
    }
    if ("SUCCESS".equals(status) || "FAILED".equals(status) || "EXPIRED".equals(status)) {
      return ToolExecutionReconciliation.failed(payload);
    }
    return ToolExecutionReconciliation.unresolved();
  }

  private <T> T deserialize(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to deserialize proposal arguments", exception);
    }
  }
}
