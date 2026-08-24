package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.modulepayment.application.BookingCancellationPreviewView;
import com.example.modulepayment.application.BookingCancellationResultView;
import com.example.modulepayment.application.BookingCancellationService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CancellationTools extends AbstractAgentTool {

  private final BookingCancellationService bookingCancellationService;

  public CancellationTools(
      ToolExecutionGuard guard, BookingCancellationService bookingCancellationService) {
    super(guard);
    this.bookingCancellationService = bookingCancellationService;
  }

  @Tool(description = "Preview booking cancellation and refund totals for selected tickets.")
  @AgentToolPolicy(
      tool = "preview_cancellation",
      operation = ToolOperation.PREVIEW,
      access = ToolAccess.AUTHENTICATED)
  public ToolResult<BookingCancellationPreviewView> preview_cancellation(
      PreviewCancellationInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "preview_cancellation",
        ToolOperation.PREVIEW,
        ToolAccess.AUTHENTICATED,
        false,
        false,
        input,
        context,
        () ->
            bookingCancellationService.preview(
                context.userId(), input.bookingId(), input.ticketIds()));
  }

  @Tool(description = "Confirm booking cancellation for selected tickets.")
  @AgentToolPolicy(
      tool = "confirm_cancellation",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<BookingCancellationResultView> confirm_cancellation(
      ConfirmCancellationInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "confirm_cancellation",
        ToolOperation.FINANCIAL,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            bookingCancellationService.cancel(
                context.userId(),
                input.bookingId(),
                input.ticketIds(),
                input.reason(),
                context.idempotencyKey(),
                input.expectedNetRefund(),
                input.expectedRefundFee()));
  }

  public record PreviewCancellationInput(@NotNull Long bookingId, @NotEmpty List<Long> ticketIds) {}

  public record ConfirmCancellationInput(
      @NotNull Long bookingId,
      @NotEmpty List<Long> ticketIds,
      String reason,
      BigDecimal expectedNetRefund,
      BigDecimal expectedRefundFee) {}
}
