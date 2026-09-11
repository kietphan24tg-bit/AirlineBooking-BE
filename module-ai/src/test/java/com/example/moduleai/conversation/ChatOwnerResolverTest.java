package com.example.moduleai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ChatOwnerResolverTest {

  @Test
  void authenticatedIdentityAlwaysOverridesGuestCookie() {
    ChatGuestTokenService guestTokenService = Mockito.mock(ChatGuestTokenService.class);
    ChatOwnerResolver resolver = new ChatOwnerResolver(guestTokenService);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    AppUserPrincipal principal =
        new AppUserPrincipal(42L, "user@example.com", "hash", Role.USER, null);
    var authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

    ChatOwner owner = resolver.resolve(authentication, request, response);

    assertThat(owner.userId()).isEqualTo(42L);
    assertThat(owner.guestToken()).isNull();
    assertThat(owner.guestTokenHash()).isNull();
    assertThat(owner.ownerKey()).isEqualTo("user:42");
    verify(guestTokenService, never()).resolveOrIssue(request, response);
  }

  @Test
  void guestIdentityUsesOnlyTheHashedTokenAsOwnerKey() {
    ChatGuestTokenService guestTokenService = Mockito.mock(ChatGuestTokenService.class);
    ChatOwnerResolver resolver = new ChatOwnerResolver(guestTokenService);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    when(guestTokenService.resolveOrIssue(request, response)).thenReturn("opaque-secret-token");
    when(guestTokenService.hash("opaque-secret-token")).thenReturn("safe-hash");

    ChatOwner owner = resolver.resolve(null, request, response);

    assertThat(owner.userId()).isNull();
    assertThat(owner.guestToken()).isEqualTo("opaque-secret-token");
    assertThat(owner.guestTokenHash()).isEqualTo("safe-hash");
    assertThat(owner.ownerKey()).isEqualTo("guest:safe-hash");
  }
}
