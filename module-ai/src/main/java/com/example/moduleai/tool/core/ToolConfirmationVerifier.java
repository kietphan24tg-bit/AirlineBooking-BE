package com.example.moduleai.tool.core;

public interface ToolConfirmationVerifier {

  ToolError verify(ToolPolicy policy, Object input, AgentToolContext context);
}
