package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.moduleai.tool.support.AncillaryCatalogToolService;
import com.example.modulebooking.application.BookingAncillarySelectionService;
import com.example.modulebooking.application.PassengerServiceSelectionCommand;
import com.example.modulebooking.application.PassengerServiceSelectionView;
import com.example.modulepayment.application.ServiceChangeConfirmView;
import com.example.modulepayment.application.ServiceChangePreviewView;
import com.example.modulepayment.application.ServiceChangeSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AncillaryTools extends AbstractAgentTool {

  private final AncillaryCatalogToolService ancillaryCatalogToolService;
  private final BookingAncillarySelectionService bookingAncillarySelectionService;
  private final ServiceChangeSettlementService serviceChangeSettlementService;

  public AncillaryTools(
      ToolExecutionGuard guard,
      AncillaryCatalogToolService ancillaryCatalogToolService,
      BookingAncillarySelectionService bookingAncillarySelectionService,
      ServiceChangeSettlementService serviceChangeSettlementService) {
    super(guard);
    this.ancillaryCatalogToolService = ancillaryCatalogToolService;
    this.bookingAncillarySelectionService = bookingAncillarySelectionService;
    this.serviceChangeSettlementService = serviceChangeSettlementService;
  }

  @Tool(
      description =
          "List selectable ancillary services. Supply both flightId and fareId from"
              + " get_manage_options for a fare-specific catalog, or omit both for the generic"
              + " catalog. Never supply only one.")
  @AgentToolPolicy(
      tool = "list_services",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<AncillaryCatalogToolService.AncillaryCatalogView> list_services(
      ListServicesInput input, ToolContext toolContext) {
    return execute(
        "list_services",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () -> ancillaryCatalogToolService.getCatalog(input.flightId(), input.fareId()));
  }

  @Tool(
      description =
          "Replace direct service selections during initial booking only. Do not call until the backend booking bridge is connected.")
  @AgentToolPolicy(
      tool = "replace_booking_services",
      operation = ToolOperation.WRITE,
      access = ToolAccess.BOOKING_VERIFIED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<PassengerServiceSelectionView> replace_booking_services(
      ReplaceBookingServicesInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "replace_booking_services",
        ToolOperation.WRITE,
        ToolAccess.BOOKING_VERIFIED,
        true,
        false,
        input,
        context,
        () ->
            bookingAncillarySelectionService.updateServices(
                context.userId(),
                input.bookingId(),
                new PassengerServiceSelectionCommand(
                    input.passengers().stream()
                        .map(
                            passenger ->
                                new PassengerServiceSelectionCommand.PassengerServicesCommand(
                                    passenger.passengerId(),
                                    passenger.selections().stream()
                                        .map(
                                            selection ->
                                                new PassengerServiceSelectionCommand
                                                    .ServiceSelectionCommand(
                                                    selection.optionId(), selection.quantity()))
                                        .toList()))
                        .toList())));
  }

  @Tool(
      description =
          "Preview ancillary service changes and settlement using the full desired selections."
              + " Quantities are final positive quantities, not deltas. Omit removed options;"
              + " never include zero or negative quantities. Preserve unrelated selections.")
  @AgentToolPolicy(
      tool = "preview_service_change",
      operation = ToolOperation.PREVIEW,
      access = ToolAccess.AUTHENTICATED)
  public ToolResult<ServiceChangePreviewView> preview_service_change(
      PreviewServiceChangeInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "preview_service_change",
        ToolOperation.PREVIEW,
        ToolAccess.AUTHENTICATED,
        false,
        false,
        input,
        context,
        () ->
            serviceChangeSettlementService.preview(
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
                    .toList()));
  }

  @Tool(
      description = "Confirm ancillary service changes using the authoritative settlement values.")
  @AgentToolPolicy(
      tool = "confirm_service_change",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<ServiceChangeConfirmView> confirm_service_change(
      ConfirmServiceChangeInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "confirm_service_change",
        ToolOperation.FINANCIAL,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            serviceChangeSettlementService.confirm(
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
                    .toList(),
                input.previewToken(),
                context.idempotencyKey(),
                input.expectedAdditionalAmount(),
                input.expectedRefundAmount(),
                input.expectedNetAmount()));
  }

  public record ListServicesInput(
      @ToolParam(required = false) Long flightId, @ToolParam(required = false) Long fareId) {}

  public record ReplaceBookingServicesInput(
      @NotNull Long bookingId, @NotEmpty List<@Valid PassengerSelectionsInput> passengers) {}

  public record PreviewServiceChangeInput(
      @NotNull Long bookingId, @NotEmpty List<@Valid PassengerSelectionsInput> passengers) {}

  public record ConfirmServiceChangeInput(
      @NotNull Long bookingId,
      @NotEmpty List<@Valid PassengerSelectionsInput> passengers,
      @ToolParam(
              required = false,
              description = "Omit; the backend binds the authoritative preview token.")
          String previewToken,
      @ToolParam(required = false) BigDecimal expectedAdditionalAmount,
      @ToolParam(required = false) BigDecimal expectedRefundAmount,
      @ToolParam(required = false) BigDecimal expectedNetAmount) {}

  public record PassengerSelectionsInput(
      @NotNull Long passengerId, @NotEmpty List<@Valid SelectionInput> selections) {}

  public record SelectionInput(
      @NotNull @Positive Long optionId,
      @ToolParam(
              description =
                  "Final positive quantity. Omit removed options instead of quantity zero.")
          @NotNull @Positive Integer quantity) {}
}
