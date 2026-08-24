package com.example.moduleai.confirmation;

import com.example.moduleai.tool.core.AgentToolContext;

public interface ToolConfirmationPresenter<I> {
  String toolName();

  Object present(I input, AgentToolContext context);
}
