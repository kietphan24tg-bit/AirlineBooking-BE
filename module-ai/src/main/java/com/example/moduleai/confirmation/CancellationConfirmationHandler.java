package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.CancellationTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.BookingCancellationPreviewView;
import com.example.modulepayment.application.BookingCancellationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

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
