package com.example.moduleai.confirmation;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.AccountTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.support.AccountToolService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class AccountProfileConfirmationHandler
    implements ToolConfirmationPresenter<AccountTools.UpdateCurrentProfileInput>,
        ToolConfirmationRevalidator<AccountTools.UpdateCurrentProfileInput> {

  private final AccountToolService accountToolService;

  AccountProfileConfirmationHandler(AccountToolService accountToolService) {
    this.accountToolService = accountToolService;
  }

  @Override
  public String toolName() {
    return "update_current_profile";
  }

  @Override
  public Object present(AccountTools.UpdateCurrentProfileInput input, AgentToolContext context) {
    requireUser(context);
    Map<String, Object> changes = new LinkedHashMap<>();
    putIfPresent(changes, "firstName", input.firstName());
    putIfPresent(changes, "lastName", input.lastName());
    putIfPresent(changes, "dob", input.dob());
    putIfPresent(changes, "nationality", input.nationality());
    putIfPresent(changes, "identifyNumber", input.identifyNumber());
    if (changes.isEmpty()) {
      throw new AppException(
          "PROFILE_CHANGE_REQUIRED", "At least one profile field must be changed", 400);
    }
    return Map.of("summary", "Confirm update to your account profile", "changes", changes);
  }

  @Override
  public void revalidate(
      AccountTools.UpdateCurrentProfileInput input,
      Object originalSnapshot,
      AgentToolContext context) {
    requireUser(context);
    accountToolService.currentProfile(context.userId());
  }

  private void requireUser(AgentToolContext context) {
    if (context.userId() == null) {
      throw new AppException("AUTHENTICATION_REQUIRED", "Authentication is required", 401);
    }
  }

  private void putIfPresent(Map<String, Object> values, String key, Object value) {
    if (value != null) {
      values.put(key, value);
    }
  }
}
