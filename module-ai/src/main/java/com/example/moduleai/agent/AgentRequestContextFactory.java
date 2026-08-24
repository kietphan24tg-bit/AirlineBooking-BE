package com.example.moduleai.agent;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class AgentRequestContextFactory {

  public AgentToolContext from(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AppUserPrincipal user)) {
      return AgentToolContext.empty();
    }
    Set<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toUnmodifiableSet());
    LinkedHashMap<String, Object> rawContext = new LinkedHashMap<>();
    rawContext.put(AgentToolContext.USER_ID_KEY, user.getUserId());
    rawContext.put(AgentToolContext.ROLES_KEY, roles);
    return new AgentToolContext(null, user.getUserId(), roles, false, null, null, null, rawContext);
  }
}
