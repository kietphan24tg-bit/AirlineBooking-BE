package com.example.moduleai.confirmation;

import com.example.moduleai.tool.SeatTools;
import com.example.modulepayment.entity.SeatChangeSettlementEntity;
import com.example.modulepayment.repository.SeatChangeSettlementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class SeatChangeExecutionReconciler implements ToolExecutionReconciler {

  private final SeatChangeSettlementRepository repository;
  private final ObjectMapper objectMapper;

  SeatChangeExecutionReconciler(
      SeatChangeSettlementRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "confirm_seat_change";
  }

  @Override
  public ToolExecutionReconciliation reconcile(ToolProposalEntity proposal) {
    SeatTools.ConfirmSeatChangeInput input =
        deserialize(proposal.getCanonicalArguments(), SeatTools.ConfirmSeatChangeInput.class);
    return repository
        .findFirstByBookingIdAndIdempotencyKeyWithItems(
            input.bookingId(), proposal.getIdempotencyKey())
        .map(this::mapSettlement)
        .orElseGet(ToolExecutionReconciliation::unresolved);
  }

  private ToolExecutionReconciliation mapSettlement(SeatChangeSettlementEntity settlement) {
    String status = normalize(settlement.getStatus());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("settlementId", settlement.getId());
    payload.put("status", settlement.getStatus());
    payload.put("settlementType", settlement.getSettlementType());
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("additionalAmount", settlement.getAdditionalAmount());
    summary.put("refundAmount", settlement.getRefundAmount());
    summary.put("netAmount", settlement.getNetAmount());
    summary.put("currency", settlement.getCurrency());
    payload.put("summary", summary);
    if (status.equals("COMPLETED")
        || status.equals("PAYMENT_REQUIRED")
        || status.equals("PAYMENT_PENDING")
        || status.equals("REFUND_PENDING")) {
      return ToolExecutionReconciliation.executed(payload);
    }
    if (status.equals("PAYMENT_FAILED") || status.equals("EXPIRED") || status.equals("CANCELLED")) {
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
