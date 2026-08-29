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
    return from(authentication, null);
  }

  public AgentToolContext from(Authentication authentication, java.util.UUID conversationId) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AppUserPrincipal user)) {
      return AgentToolContext.empty().withConversationId(conversationId);
    }
    Set<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toUnmodifiableSet());
    LinkedHashMap<String, Object> rawContext = new LinkedHashMap<>();
    if (conversationId != null) {
      rawContext.put(AgentToolContext.CONVERSATION_ID_KEY, conversationId.toString());
    }
    rawContext.put(AgentToolContext.USER_ID_KEY, user.getUserId());
    rawContext.put(AgentToolContext.ROLES_KEY, roles);
    return new AgentToolContext(
        conversationId, null, user.getUserId(), roles, false, null, null, null, rawContext);
  }
}
