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
        ToolConfirmationRevalidator<AncillaryTools.ConfirmServiceChangeInput>,
        ToolConfirmationCanonicalizer<AncillaryTools.ConfirmServiceChangeInput> {

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
    ServiceChangePreviewView original =
        objectMapper.convertValue(snapshot.get("preview"), ServiceChangePreviewView.class);
    if (!Objects.equals(input.previewToken(), fresh.previewToken())
        || !sameSummary(original.summary(), fresh.summary())
        || !Objects.equals(original.settlementType(), fresh.settlementType())) {
      throw new AppException(
          "SERVICE_CHANGE_PREVIEW_STALE",
          "Service change totals are out of date. Please review the latest preview.",
          409);
    }
  }

  @Override
  public AncillaryTools.ConfirmServiceChangeInput canonicalize(
      AncillaryTools.ConfirmServiceChangeInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    ServiceChangePreviewView preview = snapshotPreview(originalSnapshot);
    if (preview.previewToken() == null || preview.previewToken().isBlank()) {
      throw new AppException(
          "SERVICE_CHANGE_PREVIEW_STALE", "Service change preview token is missing.", 409);
    }
    return new AncillaryTools.ConfirmServiceChangeInput(
        input.bookingId(),
        input.passengers(),
        "***".equals(preview.previewToken()) ? input.previewToken() : preview.previewToken(),
        preview.summary().additionalAmount(),
        preview.summary().refundAmount(),
        preview.summary().netAmount());
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

  private ServiceChangePreviewView snapshotPreview(Object snapshot) {
    return objectMapper.convertValue(map(snapshot).get("preview"), ServiceChangePreviewView.class);
  }

  private boolean sameSummary(
      ServiceChangePreviewView.SummaryView left, ServiceChangePreviewView.SummaryView right) {
    return sameMoney(left.additionalAmount(), right.additionalAmount())
        && sameMoney(left.refundAmount(), right.refundAmount())
        && sameMoney(left.netAmount(), right.netAmount());
  }

  private boolean sameMoney(java.math.BigDecimal left, java.math.BigDecimal right) {
    return left != null && right != null && left.compareTo(right) == 0;
  }
}
