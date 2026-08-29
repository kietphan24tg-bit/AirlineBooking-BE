package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.moduleai.tool.support.AccountToolService;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class AccountTools extends AbstractAgentTool {

  private final AccountToolService accountToolService;

  public AccountTools(ToolExecutionGuard guard, AccountToolService accountToolService) {
    super(guard);
    this.accountToolService = accountToolService;
  }

  @Tool(description = "Get the profile of the currently authenticated user.")
  @AgentToolPolicy(
      tool = "get_current_profile",
      operation = ToolOperation.READ,
      access = ToolAccess.AUTHENTICATED)
  public ToolResult<AccountToolService.CurrentProfileView> get_current_profile(
      EmptyInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_current_profile",
        ToolOperation.READ,
        ToolAccess.AUTHENTICATED,
        false,
        false,
        input,
        context,
        () -> accountToolService.currentProfile(context.userId()));
  }

  @Tool(description = "Update profile fields for the currently authenticated user.")
  @AgentToolPolicy(
      tool = "update_current_profile",
      operation = ToolOperation.WRITE,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<AccountToolService.CurrentProfileView> update_current_profile(
      UpdateCurrentProfileInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "update_current_profile",
        ToolOperation.WRITE,
        ToolAccess.AUTHENTICATED,
        true,
        false,
        input,
        context,
        () ->
            accountToolService.updateProfile(
                context.userId(),
                input.firstName(),
                input.lastName(),
                input.dob(),
                input.nationality(),
                input.identifyNumber()));
  }

  @Tool(description = "Get a lightweight account overview for the authenticated user.")
  @AgentToolPolicy(
      tool = "get_account_overview",
      operation = ToolOperation.READ,
      access = ToolAccess.AUTHENTICATED)
  public ToolResult<AccountToolService.AccountOverviewView> get_account_overview(
      EmptyInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_account_overview",
        ToolOperation.READ,
        ToolAccess.AUTHENTICATED,
        false,
        false,
        input,
        context,
        () -> accountToolService.overview(context.userId()));
  }

  public record EmptyInput() {}

  public record UpdateCurrentProfileInput(
      @Size(max = 255) String firstName,
      @Size(max = 255) String lastName,
      LocalDate dob,
      @Size(max = 8) String nationality,
      @Size(max = 64) String identifyNumber) {}
}
