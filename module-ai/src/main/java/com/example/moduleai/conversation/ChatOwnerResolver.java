package com.example.moduleai.conversation;

import com.example.moduleidentity.application.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ChatOwnerResolver {

  private final ChatGuestTokenService guestTokenService;

  public ChatOwnerResolver(ChatGuestTokenService guestTokenService) {
    this.guestTokenService = guestTokenService;
  }

  public ChatOwner resolve(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal user) {
      return new ChatOwner(user.getUserId(), null, null);
    }
    String token = guestTokenService.resolveOrIssue(request, response);
    return new ChatOwner(null, token, guestTokenService.hash(token));
  }
}
