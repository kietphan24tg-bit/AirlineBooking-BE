package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.modulebooking.application.BookingSeatAssignmentService;
import com.example.modulebooking.application.FlightSeatMapService;
import com.example.modulebooking.application.SeatAssignmentCommand;
import com.example.modulebooking.application.SeatAssignmentView;
import com.example.modulebooking.application.SeatChangeCommand;
import com.example.modulepayment.application.SeatChangeConfirmView;
import com.example.modulepayment.application.SeatChangePreviewView;
import com.example.modulepayment.application.SeatChangeSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SeatTools extends AbstractAgentTool {

  private final FlightSeatMapService flightSeatMapService;
  private final BookingSeatAssignmentService bookingSeatAssignmentService;
  private final SeatChangeSettlementService seatChangeSettlementService;

  public SeatTools(
      ToolExecutionGuard guard,
      FlightSeatMapService flightSeatMapService,
      BookingSeatAssignmentService bookingSeatAssignmentService,
      SeatChangeSettlementService seatChangeSettlementService) {
    super(guard);
    this.flightSeatMapService = flightSeatMapService;
    this.bookingSeatAssignmentService = bookingSeatAssignmentService;
    this.seatChangeSettlementService = seatChangeSettlementService;
  }

  @Tool(description = "Get the current seat map and fare-specific seat eligibility for a flight.")
  @AgentToolPolicy(
      tool = "get_seat_map",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<SeatMapOutput> get_seat_map(GetSeatMapInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_seat_map",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        context,
        () ->
            SeatMapOutput.from(flightSeatMapService.getSeatMap(input.flightId(), input.fareId())));
  }

  @Tool(
      description =
          "Replace seat assignments during initial booking or check-in only. "
              + "Do not call until the backend booking bridge is connected.")
  @AgentToolPolicy(
      tool = "replace_seat_assignments",
      operation = ToolOperation.WRITE,
      access = ToolAccess.BOOKING_VERIFIED,
      confirmationRequired = true)
  public ToolResult<SeatAssignmentView> replace_seat_assignments(
      ReplaceSeatAssignmentsInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "replace_seat_assignments",
        ToolOperation.WRITE,
        ToolAccess.BOOKING_VERIFIED,
        true,
        false,
        input,
        context,
        () ->
            bookingSeatAssignmentService.assignSeats(
                context.userId(),
                input.bookingId(),
                new SeatAssignmentCommand(
                    input.assignments().stream()
                        .map(
                            assignment ->
                                new SeatAssignmentCommand.AssignmentCommand(
                                    assignment.passengerId(), assignment.seatId()))
                        .toList())));
  }

  @Tool(
      description =
          "Remove a direct seat assignment during initial booking or check-in only. "
              + "Do not call until the backend booking bridge is connected.")
  @AgentToolPolicy(
      tool = "remove_seat_assignment",
      operation = ToolOperation.WRITE,
      access = ToolAccess.BOOKING_VERIFIED,
      confirmationRequired = true)
  public ToolResult<SeatAssignmentView> remove_seat_assignment(
      ReplaceSeatAssignmentsInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "remove_seat_assignment",
        ToolOperation.WRITE,
        ToolAccess.BOOKING_VERIFIED,
        true,
        false,
        input,
        context,
        () ->
            bookingSeatAssignmentService.unassignSeats(
                context.userId(),
                input.bookingId(),
                new SeatAssignmentCommand(
                    input.assignments().stream()
                        .map(
                            assignment ->
                                new SeatAssignmentCommand.AssignmentCommand(
                                    assignment.passengerId(), assignment.seatId()))
                        .toList())));
  }

  @Tool(
      description =
          "Patch a direct seat assignment during initial booking or check-in only. "
              + "Do not call until the backend booking bridge is connected.")
  @AgentToolPolicy(
      tool = "patch_seat_assignment",
      operation = ToolOperation.WRITE,
      access = ToolAccess.BOOKING_VERIFIED,
      confirmationRequired = true)
  public ToolResult<SeatAssignmentView> patch_seat_assignment(
      PatchSeatAssignmentInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "patch_seat_assignment",
        ToolOperation.WRITE,
        ToolAccess.BOOKING_VERIFIED,
        true,
        false,
        input,
        context,
        () ->
            bookingSeatAssignmentService.changeSeat(
                context.userId(),
                input.bookingId(),
                new SeatChangeCommand(input.passengerId(), input.fromSeatId(), input.toSeatId())));
  }

  @Tool(description = "Preview manage-booking seat changes and the resulting settlement.")
  @AgentToolPolicy(
      tool = "preview_seat_change",
      operation = ToolOperation.PREVIEW,
      access = ToolAccess.AUTHENTICATED)
  public ToolResult<SeatChangePreviewView> preview_seat_change(
      PreviewSeatChangeInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "preview_seat_change",
        ToolOperation.PREVIEW,
        ToolAccess.AUTHENTICATED,
        false,
        false,
        input,
        context,
        () ->
            seatChangeSettlementService.preview(
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
                    .toList()));
  }

  @Tool(
      description =
          "Confirm manage-booking seat changes using the authoritative settlement values.")
  @AgentToolPolicy(
      tool = "confirm_seat_change",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<SeatChangeConfirmView> confirm_seat_change(
      ConfirmSeatChangeInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "confirm_seat_change",
        ToolOperation.FINANCIAL,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            seatChangeSettlementService.confirm(
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
                    .toList(),
                context.idempotencyKey(),
                input.expectedAdditionalAmount(),
                input.expectedRefundAmount(),
                input.expectedNetAmount()));
  }

  public record GetSeatMapInput(@NotNull Long flightId, @NotNull Long fareId) {}

  public record ReplaceSeatAssignmentsInput(
      @NotNull Long bookingId, @NotEmpty List<@Valid SeatAssignmentInput> assignments) {}

  public record SeatAssignmentInput(@NotNull Long passengerId, @NotNull Long seatId) {}

  public record PatchSeatAssignmentInput(
      @NotNull Long bookingId,
      @NotNull Long passengerId,
      @NotNull Long fromSeatId,
      Long toSeatId) {}

  public record SeatMapOutput(
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareCode,
      String seatSelectionPolicy,
      String includedSeatCategoryCode,
      String currency,
      AircraftLayoutOutput layout,
      LegendOutput legend,
      List<SeatRowOutput> rows) {

    private static SeatMapOutput from(FlightSeatMapService.FlightSeatMapView view) {
      return new SeatMapOutput(
          view.flightId(),
          view.fareId(),
          view.fareClassId(),
          view.fareCode(),
          view.seatSelectionPolicy(),
          view.includedSeatCategoryCode(),
          view.currency(),
          AircraftLayoutOutput.from(view.layout()),
          new LegendOutput(
              view.legend().statuses(), view.legend().seatTypes(), view.legend().seatCategories()),
          view.rows().stream().map(SeatRowOutput::from).toList());
    }
  }

  public record AircraftLayoutOutput(
      List<String> columns,
      List<String> aisleAfterColumns,
      List<SeatClassZoneOutput> seatClassZones) {

    private static AircraftLayoutOutput from(FlightSeatMapService.AircraftLayoutView view) {
      return new AircraftLayoutOutput(
          view.columns(),
          view.aisleAfterColumns(),
          view.seatClassZones().stream().map(SeatClassZoneOutput::from).toList());
    }
  }

  public record SeatClassZoneOutput(String seatClass, Integer rowStart, Integer rowEnd) {

    private static SeatClassZoneOutput from(FlightSeatMapService.SeatClassZoneView view) {
      return new SeatClassZoneOutput(view.seatClass(), view.rowStart(), view.rowEnd());
    }
  }

  public record LegendOutput(
      List<String> statuses, List<String> seatTypes, List<String> seatCategories) {}

  public record SeatRowOutput(Integer rowNumber, String seatClass, List<SeatOutput> cells) {

    private static SeatRowOutput from(FlightSeatMapService.RowView view) {
      return new SeatRowOutput(
          view.rowNumber(), view.seatClass(), view.cells().stream().map(SeatOutput::from).toList());
    }
  }

  public record SeatOutput(
      Integer rowNumber,
      String columnLetter,
      Long seatId,
      String seatNumber,
      String seatClass,
      String seatType,
      String seatCategory,
      String status,
      boolean selectable,
      boolean included,
      BigDecimal extraPrice,
      String currency,
      String reason) {

    private static SeatOutput from(FlightSeatMapService.SeatView view) {
      return new SeatOutput(
          view.rowNumber(),
          view.columnLetter(),
          view.seatId(),
          view.seatNumber(),
          view.seatClass(),
          view.seatType(),
          view.seatCategory(),
          view.status(),
          view.selectable(),
          view.included(),
          view.extraPrice(),
          view.currency(),
          view.reason());
    }
  }

  public record PreviewSeatChangeInput(
      @NotNull Long bookingId, @NotEmpty List<@Valid SeatChangeInput> changes) {}

  public record ConfirmSeatChangeInput(
      @NotNull Long bookingId,
      @NotEmpty List<@Valid SeatChangeInput> changes,
      BigDecimal expectedAdditionalAmount,
      BigDecimal expectedRefundAmount,
      BigDecimal expectedNetAmount) {}

  public record SeatChangeInput(
      @NotNull Long passengerId, @NotNull Long flightId, Long fromSeatId, Long toSeatId) {}
}
