package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.AncillaryTools;
import com.example.moduleai.tool.CancellationTools;
import com.example.moduleai.tool.CheckInTools;
import com.example.moduleai.tool.SeatTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulecheckin.application.CheckInContinueCommand;
import com.example.modulecheckin.application.CheckInContinueService;
import com.example.modulepayment.application.BookingCancellationPreviewView;
import com.example.modulepayment.application.BookingCancellationService;
import com.example.modulepayment.application.SeatChangePreviewView;
import com.example.modulepayment.application.SeatChangeSettlementService;
import com.example.modulepayment.application.ServiceChangePreviewView;
import com.example.modulepayment.application.ServiceChangeSettlementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

@Component
class ServiceChangeConfirmationHandler
    implements ToolConfirmationPresenter<AncillaryTools.ConfirmServiceChangeInput>,
        ToolConfirmationRevalidator<AncillaryTools.ConfirmServiceChangeInput> {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ServiceChangeSettlementService serviceChangeSettlementService;
  private final ObjectMapper objectMapper;

  ServiceChangeConfirmationHandler(
      ServiceChangeSettlementService serviceChangeSettlementService, ObjectMapper objectMapper) {
    this.serviceChangeSettlementService = serviceChangeSettlementService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "confirm_service_change";
  }

  @Override
  public Object present(AncillaryTools.ConfirmServiceChangeInput input, AgentToolContext context) {
    ServiceChangePreviewView preview = preview(input, context);
    return Map.of(
        "summary", "Confirm service change for booking " + input.bookingId(),
        "bookingId", input.bookingId(),
        "preview", objectMapper.convertValue(preview, MAP_TYPE));
  }

  @Override
  public void revalidate(
      AncillaryTools.ConfirmServiceChangeInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    ServiceChangePreviewView fresh = preview(input, context);
    Map<String, Object> snapshot = map(originalSnapshot);
    Map<String, Object> originalPreview = map(snapshot.get("preview"));
    Map<String, Object> freshPreview = objectMapper.convertValue(fresh, MAP_TYPE);
    if (!Objects.equals(originalPreview.get("previewToken"), freshPreview.get("previewToken"))
        || !Objects.equals(originalPreview.get("summary"), freshPreview.get("summary"))
        || !Objects.equals(
            originalPreview.get("settlementType"), freshPreview.get("settlementType"))) {
      throw new AppException(
          "SERVICE_CHANGE_PREVIEW_STALE",
          "Service change totals are out of date. Please review the latest preview.",
          409);
    }
  }

  private ServiceChangePreviewView preview(
      AncillaryTools.ConfirmServiceChangeInput input, AgentToolContext context) {
    return serviceChangeSettlementService.preview(
        context.userId(),
        input.bookingId(),
        input.passengers().stream()
            .map(
                passenger ->
                    new ServiceChangeSettlementService.PassengerSelectionsCommand(
                        passenger.passengerId(),
                        passenger.selections().stream()
                            .map(
                                selection ->
                                    new ServiceChangeSettlementService.SelectionCommand(
                                        selection.optionId(), selection.quantity()))
                            .toList()))
            .toList());
  }

  private Map<String, Object> map(Object value) {
    return objectMapper.convertValue(value, MAP_TYPE);
  }
}

@Component
class CancellationConfirmationHandler
    implements ToolConfirmationPresenter<CancellationTools.ConfirmCancellationInput>,
        ToolConfirmationRevalidator<CancellationTools.ConfirmCancellationInput> {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final BookingCancellationService bookingCancellationService;
  private final ObjectMapper objectMapper;

  CancellationConfirmationHandler(
      BookingCancellationService bookingCancellationService, ObjectMapper objectMapper) {
    this.bookingCancellationService = bookingCancellationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "confirm_cancellation";
  }

  @Override
  public Object present(
      CancellationTools.ConfirmCancellationInput input, AgentToolContext context) {
    BookingCancellationPreviewView preview = preview(input, context);
    return Map.of(
        "summary", "Confirm cancellation for booking " + input.bookingId(),
        "bookingId", input.bookingId(),
        "preview", objectMapper.convertValue(preview, MAP_TYPE));
  }

  @Override
  public void revalidate(
      CancellationTools.ConfirmCancellationInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    BookingCancellationPreviewView fresh = preview(input, context);
    Map<String, Object> snapshot = map(originalSnapshot);
    Map<String, Object> originalPreview = map(snapshot.get("preview"));
    Map<String, Object> freshPreview = objectMapper.convertValue(fresh, MAP_TYPE);
    if (!Objects.equals(originalPreview.get("summary"), freshPreview.get("summary"))) {
      throw new AppException(
          "CANCELLATION_PREVIEW_STALE",
          "Cancellation preview is stale. Please preview the latest refund totals.",
          409);
    }
  }

  private BookingCancellationPreviewView preview(
      CancellationTools.ConfirmCancellationInput input, AgentToolContext context) {
    return bookingCancellationService.preview(
        context.userId(), input.bookingId(), input.ticketIds());
  }

  private Map<String, Object> map(Object value) {
    return objectMapper.convertValue(value, MAP_TYPE);
  }
}

@Component
class CheckInConfirmationHandler
    implements ToolConfirmationPresenter<CheckInTools.ConfirmCheckInInput>,
        ToolConfirmationRevalidator<CheckInTools.ConfirmCheckInInput> {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final CheckInContinueService checkInContinueService;
  private final ObjectMapper objectMapper;

  CheckInConfirmationHandler(
      CheckInContinueService checkInContinueService, ObjectMapper objectMapper) {
    this.checkInContinueService = checkInContinueService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String toolName() {
    return "confirm_check_in";
  }

  @Override
  public Object present(CheckInTools.ConfirmCheckInInput input, AgentToolContext context) {
    Object continueView = checkInContinueService.continueCheckIn(commandOf(input));
    return Map.of(
        "summary", "Confirm online check-in for PNR " + input.pnrCode(),
        "pnrCode", input.pnrCode(),
        "preview", objectMapper.convertValue(continueView, MAP_TYPE));
  }

  @Override
  public void revalidate(
      CheckInTools.ConfirmCheckInInput input, Object originalSnapshot, AgentToolContext context) {
    checkInContinueService.continueCheckIn(commandOf(input));
  }

  private CheckInContinueCommand commandOf(CheckInTools.ConfirmCheckInInput input) {
    return new CheckInContinueCommand(
        input.pnrCode(),
        input.lastName(),
        input.segments().stream()
            .map(
                segment ->
                    new CheckInContinueCommand.SegmentSelection(
                        segment.flightId(), List.copyOf(segment.passengerIds())))
            .toList());
  }
}
