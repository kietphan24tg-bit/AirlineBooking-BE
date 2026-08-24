package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.modulecheckin.application.CheckInConfirmCommand;
import com.example.modulecheckin.application.CheckInConfirmService;
import com.example.modulecheckin.application.CheckInConfirmView;
import com.example.modulecheckin.application.CheckInContinueCommand;
import com.example.modulecheckin.application.CheckInContinueService;
import com.example.modulecheckin.application.CheckInContinueView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CheckInTools extends AbstractAgentTool {

  private final CheckInContinueService checkInContinueService;
  private final CheckInConfirmService checkInConfirmService;

  public CheckInTools(
      ToolExecutionGuard guard,
      CheckInContinueService checkInContinueService,
      CheckInConfirmService checkInConfirmService) {
    super(guard);
    this.checkInContinueService = checkInContinueService;
    this.checkInConfirmService = checkInConfirmService;
  }

  @Tool(
      description = "Load the current online check-in state for selected passengers and segments.")
  @AgentToolPolicy(
      tool = "continue_check_in",
      operation = ToolOperation.PREVIEW,
      access = ToolAccess.PUBLIC)
  public ToolResult<CheckInContinueView> continue_check_in(
      ContinueCheckInInput input, ToolContext toolContext) {
    return execute(
        "continue_check_in",
        ToolOperation.PREVIEW,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () ->
            checkInContinueService.continueCheckIn(
                new CheckInContinueCommand(
                    input.pnrCode(),
                    input.lastName(),
                    input.segments().stream()
                        .map(
                            segment ->
                                new CheckInContinueCommand.SegmentSelection(
                                    segment.flightId(), segment.passengerIds()))
                        .toList())));
  }

  @Tool(description = "Confirm online check-in and issue boarding passes for selected passengers.")
  @AgentToolPolicy(
      tool = "confirm_check_in",
      operation = ToolOperation.WRITE,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true)
  public ToolResult<CheckInConfirmView> confirm_check_in(
      ConfirmCheckInInput input, ToolContext toolContext) {
    return execute(
        "confirm_check_in",
        ToolOperation.WRITE,
        ToolAccess.PUBLIC,
        true,
        false,
        input,
        AgentToolContext.from(toolContext),
        () ->
            checkInConfirmService.confirmCheckIn(
                new CheckInConfirmCommand(
                    input.pnrCode(),
                    input.lastName(),
                    input.segments().stream()
                        .map(
                            segment ->
                                new CheckInConfirmCommand.SegmentSelection(
                                    segment.flightId(), segment.passengerIds()))
                        .toList())));
  }

  public record ContinueCheckInInput(
      @NotBlank String pnrCode,
      @NotBlank String lastName,
      @NotEmpty List<@Valid SegmentSelectionInput> segments) {}

  public record ConfirmCheckInInput(
      @NotBlank String pnrCode,
      @NotBlank String lastName,
      @NotEmpty List<@Valid SegmentSelectionInput> segments) {}

  public record SegmentSelectionInput(
      @NotNull Long flightId, @NotEmpty List<@NotNull Long> passengerIds) {}
}
