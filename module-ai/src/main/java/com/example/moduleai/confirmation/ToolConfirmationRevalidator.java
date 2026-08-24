package com.example.moduleai.confirmation;

import com.example.moduleai.tool.core.AgentToolContext;

public interface ToolConfirmationRevalidator<I> {
  String toolName();

  void revalidate(I input, Object originalSnapshot, AgentToolContext context);
}
