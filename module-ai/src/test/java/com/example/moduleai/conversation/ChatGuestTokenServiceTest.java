package com.example.moduleai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.config.AiChatProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;

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
    properties.setGuestCookieMaxAge(Duration.ofDays(7));
    properties.setGuestCookieSameSite("Lax");
    properties.setGuestCookieSecure(true);
    properties.setGuestCookieHttpOnly(true);
    ChatGuestTokenService service = new ChatGuestTokenService(properties);
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    when(request.getCookies()).thenReturn(null);

    String token = service.resolveOrIssue(request, response);

    assertThat(token).isNotBlank();
    assertThat(token).hasSize(43);
    assertThat(service.hash(token)).hasSize(64);
    ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
    verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), header.capture());
    assertThat(header.getValue())
        .contains("chat_guest=")
        .contains("Path=/api/chatbot")
        .contains("Max-Age=604800")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=Lax");
  }
}
