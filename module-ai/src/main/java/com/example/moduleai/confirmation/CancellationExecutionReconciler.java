package com.example.moduleai.confirmation;

import com.example.moduleai.tool.CancellationTools;
import com.example.modulepayment.entity.BookingCancellationEntity;
import com.example.modulepayment.repository.BookingCancellationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class CancellationExecutionReconciler implements ToolExecutionReconciler {

  private final BookingCancellationRepository repository;
  private final ObjectMapper objectMapper;

  CancellationExecutionReconciler(
      BookingCancellationRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "confirm_cancellation";
  }

  @Override
  public ToolExecutionReconciliation reconcile(ToolProposalEntity proposal) {
    CancellationTools.ConfirmCancellationInput input =
        deserialize(
            proposal.getCanonicalArguments(), CancellationTools.ConfirmCancellationInput.class);
    return repository
        .findByBookingIdAndIdempotencyKeyWithItems(input.bookingId(), proposal.getIdempotencyKey())
        .map(this::mapCancellation)
        .orElseGet(ToolExecutionReconciliation::unresolved);
  }

  private ToolExecutionReconciliation mapCancellation(BookingCancellationEntity cancellation) {
    String status = normalize(cancellation.getRefundStatus());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("cancellationId", cancellation.getId());
    payload.put("refundStatus", cancellation.getRefundStatus());
    payload.put("provider", cancellation.getProvider());
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("netRefund", cancellation.getNetRefundTotal());
    summary.put("refundFee", cancellation.getRefundFeeTotal());
    summary.put("currency", cancellation.getCurrency());
    payload.put("summary", summary);
    if (status.equals("SUCCEEDED")) {
      return ToolExecutionReconciliation.executed(payload);
    }
    if (status.equals("FAILED")) {
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

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
