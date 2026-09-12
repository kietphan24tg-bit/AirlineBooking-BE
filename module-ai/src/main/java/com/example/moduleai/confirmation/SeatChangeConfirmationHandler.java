package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.SeatTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.SeatChangePreviewView;
import com.example.modulepayment.application.SeatChangeSettlementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class SeatChangeConfirmationHandler
    implements ToolConfirmationPresenter<SeatTools.ConfirmSeatChangeInput>,
        ToolConfirmationRevalidator<SeatTools.ConfirmSeatChangeInput>,
        ToolConfirmationCanonicalizer<SeatTools.ConfirmSeatChangeInput> {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final SeatChangeSettlementService seatChangeSettlementService;
  private final ObjectMapper objectMapper;

  SeatChangeConfirmationHandler(
      SeatChangeSettlementService seatChangeSettlementService, ObjectMapper objectMapper) {
    this.seatChangeSettlementService = seatChangeSettlementService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "confirm_seat_change";
  }

  @Override
  public Object present(SeatTools.ConfirmSeatChangeInput input, AgentToolContext context) {
    SeatChangePreviewView preview = preview(input, context);
    return Map.of(
        "summary", "Confirm seat change for booking " + input.bookingId(),
        "bookingId", input.bookingId(),
        "preview", objectMapper.convertValue(preview, MAP_TYPE));
  }

  @Override
  public void revalidate(
      SeatTools.ConfirmSeatChangeInput input, Object originalSnapshot, AgentToolContext context) {
    SeatChangePreviewView fresh = preview(input, context);
    Map<String, Object> snapshot = map(originalSnapshot);
    SeatChangePreviewView original =
        objectMapper.convertValue(snapshot.get("preview"), SeatChangePreviewView.class);
    if (!sameSummary(original.summary(), fresh.summary())
        || !Objects.equals(original.settlementType(), fresh.settlementType())) {
      throw new AppException(
          "SEAT_CHANGE_PREVIEW_STALE",
          "Seat change totals are out of date. Please review the latest preview.",
          409);
    }
  }

  @Override
  public SeatTools.ConfirmSeatChangeInput canonicalize(
      SeatTools.ConfirmSeatChangeInput input, Object originalSnapshot, AgentToolContext context) {
    SeatChangePreviewView preview = snapshotPreview(originalSnapshot);
    return new SeatTools.ConfirmSeatChangeInput(
        input.bookingId(),
        input.changes(),
        preview.summary().additionalAmount(),
        preview.summary().refundAmount(),
        preview.summary().netAmount());
  }

  private SeatChangePreviewView preview(
      SeatTools.ConfirmSeatChangeInput input, AgentToolContext context) {
    return seatChangeSettlementService.preview(
        context.userId(),
        input.bookingId(),
        input.changes().stream()
            .map(
                change ->
                    new SeatChangeSettlementService.ChangeCommand(
                        change.passengerId(),
                        change.flightId(),
                        change.fromSeatId(),
                        change.toSeatId()))
            .toList());
  }

  private Map<String, Object> map(Object value) {
    return objectMapper.convertValue(value, MAP_TYPE);
  }

  private SeatChangePreviewView snapshotPreview(Object snapshot) {
    return objectMapper.convertValue(map(snapshot).get("preview"), SeatChangePreviewView.class);
  }

  private boolean sameSummary(
      SeatChangePreviewView.SummaryView left, SeatChangePreviewView.SummaryView right) {
    return sameMoney(left.additionalAmount(), right.additionalAmount())
        && sameMoney(left.refundAmount(), right.refundAmount())
        && sameMoney(left.netAmount(), right.netAmount());
  }

  private boolean sameMoney(java.math.BigDecimal left, java.math.BigDecimal right) {
    return left != null && right != null && left.compareTo(right) == 0;
  }
}
