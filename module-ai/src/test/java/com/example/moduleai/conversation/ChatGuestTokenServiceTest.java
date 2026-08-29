package com.example.moduleai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.config.AiChatProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatGuestTokenServiceTest {

  @Test
  void resolvesExistingCookieAndRefreshesIt() {
    AiChatProperties properties = new AiChatProperties();
    properties.setGuestCookieName("chat_guest");
    ChatGuestTokenService service = new ChatGuestTokenService(properties);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    Cookie cookie = new Cookie("chat_guest", "guest-token");
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});

    String token = service.resolveOrIssue(request, response);

    assertThat(token).isEqualTo("guest-token");
    verify(response)
        .addHeader(
            org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpHeaders.SET_COOKIE),
            org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void issuesTokenWhenMissingCookie() {
    AiChatProperties properties = new AiChatProperties();
    ChatGuestTokenService service = new ChatGuestTokenService(properties);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    when(request.getCookies()).thenReturn(null);

    String token = service.resolveOrIssue(request, response);

    assertThat(token).isNotBlank();
    assertThat(service.hash(token)).hasSize(64);
    verify(response)
        .addHeader(
            org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpHeaders.SET_COOKIE),
            org.mockito.ArgumentMatchers.anyString());
  }
}
