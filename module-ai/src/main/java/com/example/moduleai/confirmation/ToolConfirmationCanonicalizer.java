package com.example.moduleai.confirmation;

import com.example.moduleai.tool.core.AgentToolContext;

public interface ToolConfirmationCanonicalizer<I> {
  I canonicalize(I input, Object originalSnapshot, AgentToolContext context);
}
