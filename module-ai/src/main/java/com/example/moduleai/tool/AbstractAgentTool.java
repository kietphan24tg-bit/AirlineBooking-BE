package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolPolicy;
import com.example.moduleai.tool.core.ToolResult;
import java.util.function.Supplier;

abstract class AbstractAgentTool {

  private final ToolExecutionGuard guard;

  protected AbstractAgentTool(ToolExecutionGuard guard) {
    this.guard = guard;
  }

  /**
   * Legacy bridge for existing tools. In production {@link ToolExecutionGuard} resolves the policy
   * from {@code ToolCatalog}; the duplicated arguments are ignored.
   */
  @Deprecated(forRemoval = true)
  protected <I, O> ToolResult<O> execute(
      String tool,
      ToolOperation operation,
      ToolAccess access,
      boolean confirmationRequired,
      boolean idempotencyRequired,
      I input,
      AgentToolContext context,
      Supplier<O> action) {
    return guard.execute(
        new ToolPolicy(tool, operation, access, confirmationRequired, idempotencyRequired),
        input,
        context,
        action);
  }

  protected <I, O> ToolResult<O> execute(
      String tool, I input, AgentToolContext context, Supplier<O> action) {
    return guard.execute(tool, input, context, action);
  }
}
