package com.example.appbootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.config.AiChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HttpCookieProfileTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withPropertyValues("spring.profiles.active=http");

  @Test
  void httpProfileBindsBrowserCompatibleCookiesWithoutDisablingHttpOnly() {
    contextRunner.run(
        context -> {
          Binder binder = Binder.get(context.getEnvironment());
          AiChatProperties chat = binder.bind("app.ai.chat", AiChatProperties.class).get();
          assertThat(chat.isGuestCookieSecure()).isFalse();
          assertThat(chat.getGuestCookieSameSite()).isEqualTo("Lax");
          assertThat(chat.isGuestCookieHttpOnly()).isTrue();
          assertThat(binder.bind("app.auth.refresh-cookie.secure", Boolean.class).get()).isFalse();
          assertThat(binder.bind("app.auth.refresh-cookie.same-site", String.class).get())
              .isEqualTo("Lax");
          assertThat(binder.bind("app.auth.refresh-cookie.http-only", Boolean.class).get())
              .isTrue();
        });
  }

  @Test
  void explicitCookieSettingsOverrideHttpProfileDefaults() {
    contextRunner
        .withPropertyValues(
            "APP_AI_CHAT_GUEST_COOKIE_SECURE=true",
            "APP_AI_CHAT_GUEST_COOKIE_SAME_SITE=None",
            "APP_AUTH_REFRESH_COOKIE_SECURE=true",
            "APP_AUTH_REFRESH_COOKIE_SAME_SITE=Strict")
        .run(
            context -> {
              Binder binder = Binder.get(context.getEnvironment());
              AiChatProperties chat = binder.bind("app.ai.chat", AiChatProperties.class).get();
              assertThat(chat.isGuestCookieSecure()).isTrue();
              assertThat(chat.getGuestCookieSameSite()).isEqualTo("None");
              assertThat(binder.bind("app.auth.refresh-cookie.secure", Boolean.class).get())
                  .isTrue();
              assertThat(binder.bind("app.auth.refresh-cookie.same-site", String.class).get())
                  .isEqualTo("Strict");
            });
  }
}
