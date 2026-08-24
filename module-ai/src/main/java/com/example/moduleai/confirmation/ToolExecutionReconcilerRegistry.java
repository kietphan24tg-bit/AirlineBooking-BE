package com.example.moduleai.confirmation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ToolExecutionReconcilerRegistry {

  private final Map<String, ToolExecutionReconciler> reconcilers;

  public ToolExecutionReconcilerRegistry(List<ToolExecutionReconciler> reconcilers) {
    this.reconcilers =
        reconcilers.stream()
            .collect(Collectors.toMap(ToolExecutionReconciler::toolName, reconciler -> reconciler));
  }

  public ToolExecutionReconciler get(String toolName) {
    return reconcilers.get(toolName);
  }
}
