package com.example.moduleidentity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

  private boolean enabled = true;
  private boolean failOpen = true;
  private AuthWindow login = new AuthWindow(Duration.ofMinutes(10), 5, 20, 5);
  private AuthWindow passwordReset = new AuthWindow(Duration.ofMinutes(15), 3, 10, 3);
  private RefreshWindow refresh = new RefreshWindow(Duration.ofMinutes(5), 20, 60);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }

  public AuthWindow getLogin() {
    return login;
  }

  public void setLogin(AuthWindow login) {
    this.login = login;
  }

  public AuthWindow getPasswordReset() {
    return passwordReset;
  }

  public void setPasswordReset(AuthWindow passwordReset) {
    this.passwordReset = passwordReset;
  }

  public RefreshWindow getRefresh() {
    return refresh;
  }

  public void setRefresh(RefreshWindow refresh) {
    this.refresh = refresh;
  }

  public static class AuthWindow {
    private Duration window = Duration.ofMinutes(10);
    private int emailLimit = 5;
    private int clientLimit = 20;
    private int pairLimit = 5;

    public AuthWindow() {}

    public AuthWindow(Duration window, int emailLimit, int clientLimit, int pairLimit) {
      this.window = window;
      this.emailLimit = emailLimit;
      this.clientLimit = clientLimit;
      this.pairLimit = pairLimit;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }

    public int getEmailLimit() {
      return emailLimit;
    }

    public void setEmailLimit(int emailLimit) {
      this.emailLimit = emailLimit;
    }

    public int getClientLimit() {
      return clientLimit;
    }

    public void setClientLimit(int clientLimit) {
      this.clientLimit = clientLimit;
    }

    public int getPairLimit() {
      return pairLimit;
    }

    public void setPairLimit(int pairLimit) {
      this.pairLimit = pairLimit;
    }
  }

  public static class RefreshWindow {
    private Duration window = Duration.ofMinutes(5);
    private int userLimit = 20;
    private int clientLimit = 60;

    public RefreshWindow() {}

    public RefreshWindow(Duration window, int userLimit, int clientLimit) {
      this.window = window;
      this.userLimit = userLimit;
      this.clientLimit = clientLimit;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }

    public int getUserLimit() {
      return userLimit;
    }

    public void setUserLimit(int userLimit) {
      this.userLimit = userLimit;
    }

    public int getClientLimit() {
      return clientLimit;
    }

    public void setClientLimit(int clientLimit) {
      this.clientLimit = clientLimit;
    }
  }
}
