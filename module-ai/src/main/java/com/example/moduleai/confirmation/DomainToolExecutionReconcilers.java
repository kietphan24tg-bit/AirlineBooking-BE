package com.example.moduleai.confirmation;

import com.example.moduleai.tool.AncillaryTools;
import com.example.moduleai.tool.CancellationTools;
import com.example.moduleai.tool.SeatTools;
import com.example.modulepayment.entity.BookingCancellationEntity;
import com.example.modulepayment.entity.SeatChangeSettlementEntity;
import com.example.modulepayment.entity.ServiceChangeSettlementEntity;
import com.example.modulepayment.repository.BookingCancellationRepository;
import com.example.modulepayment.repository.SeatChangeSettlementRepository;
import com.example.modulepayment.repository.ServiceChangeSettlementRepository;
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

@Component
class ServiceChangeExecutionReconciler implements ToolExecutionReconciler {

  private final ServiceChangeSettlementRepository repository;
  private final ObjectMapper objectMapper;

  ServiceChangeExecutionReconciler(
      ServiceChangeSettlementRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "confirm_service_change";
  }

  @Override
  public ToolExecutionReconciliation reconcile(ToolProposalEntity proposal) {
    AncillaryTools.ConfirmServiceChangeInput input =
        deserialize(
            proposal.getCanonicalArguments(), AncillaryTools.ConfirmServiceChangeInput.class);
    return repository
        .findFirstByBookingIdAndIdempotencyKeyWithItems(
            input.bookingId(), proposal.getIdempotencyKey())
        .map(this::mapSettlement)
        .orElseGet(ToolExecutionReconciliation::unresolved);
  }

  private ToolExecutionReconciliation mapSettlement(ServiceChangeSettlementEntity settlement) {
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
