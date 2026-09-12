package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.CancellationTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.BookingCancellationPreviewView;
import com.example.modulepayment.application.BookingCancellationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class CancellationConfirmationHandler
    implements ToolConfirmationPresenter<CancellationTools.ConfirmCancellationInput>,
        ToolConfirmationRevalidator<CancellationTools.ConfirmCancellationInput>,
        ToolConfirmationCanonicalizer<CancellationTools.ConfirmCancellationInput> {

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
    BookingCancellationPreviewView original =
        objectMapper.convertValue(snapshot.get("preview"), BookingCancellationPreviewView.class);
    if (!sameMoney(original.summary().netRefund(), fresh.summary().netRefund())
        || !sameMoney(original.summary().refundFee(), fresh.summary().refundFee())) {
      throw new AppException(
          "CANCELLATION_PREVIEW_STALE",
          "Cancellation preview is stale. Please preview the latest refund totals.",
          409);
    }
  }

  @Override
  public CancellationTools.ConfirmCancellationInput canonicalize(
      CancellationTools.ConfirmCancellationInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    BookingCancellationPreviewView preview = snapshotPreview(originalSnapshot);
    return new CancellationTools.ConfirmCancellationInput(
        input.bookingId(),
        input.ticketIds(),
        input.reason(),
        preview.summary().netRefund(),
        preview.summary().refundFee());
  }

  private BookingCancellationPreviewView preview(
      CancellationTools.ConfirmCancellationInput input, AgentToolContext context) {
    return bookingCancellationService.preview(
        context.userId(), input.bookingId(), input.ticketIds());
  }

  private Map<String, Object> map(Object value) {
    return objectMapper.convertValue(value, MAP_TYPE);
  }

  private BookingCancellationPreviewView snapshotPreview(Object snapshot) {
    return objectMapper.convertValue(
        map(snapshot).get("preview"), BookingCancellationPreviewView.class);
  }

  private boolean sameMoney(java.math.BigDecimal left, java.math.BigDecimal right) {
    return left != null && right != null && left.compareTo(right) == 0;
  }
}
