package com.example.moduleai.confirmation;

import com.example.moduleai.tool.CheckInTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulecheckin.application.CheckInContinueCommand;
import com.example.modulecheckin.application.CheckInContinueService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

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
