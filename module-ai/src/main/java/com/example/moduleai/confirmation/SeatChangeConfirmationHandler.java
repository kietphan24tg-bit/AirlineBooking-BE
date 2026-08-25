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
        ToolConfirmationRevalidator<SeatTools.ConfirmSeatChangeInput> {

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
    Map<String, Object> originalPreview = map(snapshot.get("preview"));
    Map<String, Object> freshPreview = objectMapper.convertValue(fresh, MAP_TYPE);
    if (!Objects.equals(originalPreview.get("summary"), freshPreview.get("summary"))
        || !Objects.equals(
            originalPreview.get("settlementType"), freshPreview.get("settlementType"))) {
      throw new AppException(
          "SEAT_CHANGE_PREVIEW_STALE",
          "Seat change totals are out of date. Please review the latest preview.",
          409);
    }
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
}
