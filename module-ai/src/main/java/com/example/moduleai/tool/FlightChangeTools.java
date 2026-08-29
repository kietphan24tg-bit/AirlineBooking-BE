package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class FlightChangeTools extends AbstractAgentTool {

  public FlightChangeTools(ToolExecutionGuard guard) {
    super(guard);
  }

  @Tool(
      description =
          "Quote a flight change and settlement impact. Do not call until the backend quote bridge is connected.")
  @AgentToolPolicy(
      tool = "quote_flight_change",
      operation = ToolOperation.PREVIEW,
      access = ToolAccess.AUTHENTICATED)
  public ToolResult<UnavailableToolData> quote_flight_change(
      EmptyInput input, ToolContext toolContext) {
    return unavailable("quote_flight_change");
  }

  @Tool(
      description =
          "Confirm a flight change after a fresh quote. Do not call until the backend quote bridge is connected.")
  @AgentToolPolicy(
      tool = "confirm_flight_change",
      operation = ToolOperation.FINANCIAL,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<UnavailableToolData> confirm_flight_change(
      EmptyInput input, ToolContext toolContext) {
    return unavailable("confirm_flight_change");
  }

  private ToolResult<UnavailableToolData> unavailable(String tool) {
    return ToolResult.failure(
        tool,
        com.example.moduleai.tool.core.ToolError.unavailable(
            "TOOL_BACKEND_NOT_CONNECTED",
            "The backend implementation for this tool is not connected yet"));
  }

  public record EmptyInput() {}

  public record UnavailableToolData(String reason) {}
}
