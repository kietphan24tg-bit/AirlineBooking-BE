package com.example.moduleai.confirmation;

public interface ToolExecutionReconciler {

  String toolName();

  ToolExecutionReconciliation reconcile(ToolProposalEntity proposal);
}
