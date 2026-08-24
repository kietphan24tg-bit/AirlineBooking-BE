package com.example.moduleai.confirmation;

import com.example.moduleai.tool.core.AgentToolContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ToolConfirmationRegistry {

  private final Map<String, ToolConfirmationPresenter<?>> presenters;
  private final Map<String, ToolConfirmationRevalidator<?>> revalidators;

  public ToolConfirmationRegistry(
      List<ToolConfirmationPresenter<?>> presenterList,
      List<ToolConfirmationRevalidator<?>> revalidatorList) {
    this.presenters =
        presenterList.stream()
            .collect(Collectors.toMap(ToolConfirmationPresenter::toolName, p -> p));
    this.revalidators =
        revalidatorList.stream()
            .collect(Collectors.toMap(ToolConfirmationRevalidator::toolName, r -> r));
  }

  public ToolConfirmationPresenter<?> getPresenter(String toolName) {
    return presenters.get(toolName);
  }

  public ToolConfirmationRevalidator<?> getRevalidator(String toolName) {
    return revalidators.get(toolName);
  }

  public boolean hasCompleteHandler(String toolName) {
    return presenters.containsKey(toolName) && revalidators.containsKey(toolName);
  }

  @SuppressWarnings("unchecked")
  public Object present(String toolName, Object input, AgentToolContext context) {
    ToolConfirmationPresenter<Object> presenter =
        (ToolConfirmationPresenter<Object>) presenters.get(toolName);
    if (presenter == null) {
      return input;
    }
    Object presented = presenter.present(input, context);
    return presented == null ? input : presented;
  }

  @SuppressWarnings("unchecked")
  public void revalidate(
      String toolName, Object input, Object originalSnapshot, AgentToolContext context) {
    ToolConfirmationRevalidator<Object> revalidator =
        (ToolConfirmationRevalidator<Object>) revalidators.get(toolName);
    if (revalidator != null) {
      revalidator.revalidate(input, originalSnapshot, context);
    }
  }
}
