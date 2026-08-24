package com.example.moduleai.confirmation;

public record ToolExecutionReconciliation(
    ToolProposalStatus status, Object safeResult, Object safeError) {

  public static ToolExecutionReconciliation executed(Object safeResult) {
    return new ToolExecutionReconciliation(ToolProposalStatus.EXECUTED, safeResult, null);
  }

  public static ToolExecutionReconciliation failed(Object safeError) {
    return new ToolExecutionReconciliation(ToolProposalStatus.FAILED, null, safeError);
  }

  public static ToolExecutionReconciliation unresolved() {
    return new ToolExecutionReconciliation(ToolProposalStatus.EXECUTION_UNKNOWN, null, null);
  }
}
