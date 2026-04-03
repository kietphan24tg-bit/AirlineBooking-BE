package com.example.appbootstrap.security;

import com.example.moduleidentity.security.AppUserPrincipal;
import java.util.Arrays;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("authz") // đặt tên bean để có thể sử dụng trong SpEL
public class AuthorizationService {

  public boolean isCurrentUser(Long userId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
      return false;
    }
    return principal.getUserId().equals(userId);
  }

  public boolean hasAnyRole(String... roles) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }

    return authentication.getAuthorities().stream()
        .map(authority -> authority.getAuthority())
        .anyMatch(
            authority -> Arrays.stream(roles).anyMatch(role -> authority.equals("ROLE_" + role)));
  }

  public boolean isCurrentUserOrHasAnyRole(Long userId, String... roles) {
    return isCurrentUser(userId) || hasAnyRole(roles);
  }
}
