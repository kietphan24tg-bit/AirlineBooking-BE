package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.AncillaryTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.ServiceChangePreviewView;
import com.example.modulepayment.application.ServiceChangeSettlementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

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
