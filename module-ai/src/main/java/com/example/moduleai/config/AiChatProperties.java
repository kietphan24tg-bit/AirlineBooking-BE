package com.example.moduleai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.chat")
public class AiChatProperties {

  private Duration inactivityTimeout = Duration.ofHours(24);
  private int recentTurns = 8;
  private int contextBudgetChars = 24_000;
  private int summaryMaxChars = 4_000;
  private Duration userRetention = Duration.ofDays(30);
  private Duration guestRetention = Duration.ofDays(7);
  private Duration guestCookieMaxAge = Duration.ofDays(7);
  private Duration maintenanceDelay = Duration.ofMinutes(1);
  private int summaryBatchSize = 10;
  private Duration summaryClaimTimeout = Duration.ofMinutes(2);
  private int summaryMaxAttempts = 3;
  private boolean persistenceEnabled = true;
  private boolean memoryEnabled = true;
  private String guestCookieName = "chat_guest";
  private String guestCookiePath = "/api/chatbot";
  private String guestCookieDomain;
  private boolean guestCookieSecure = true;
  private boolean guestCookieHttpOnly = true;
  private String guestCookieSameSite = "None";

  public Duration getInactivityTimeout() {
    return inactivityTimeout;
  }

  public void setInactivityTimeout(Duration inactivityTimeout) {
    this.inactivityTimeout = inactivityTimeout;
  }

  public int getRecentTurns() {
    return recentTurns;
  }

  public void setRecentTurns(int recentTurns) {
    this.recentTurns = recentTurns;
  }

  public int getContextBudgetChars() {
    return Math.max(0, contextBudgetChars);
  }

  public void setContextBudgetChars(int contextBudgetChars) {
    this.contextBudgetChars = contextBudgetChars;
  }

  public int getSummaryMaxChars() {
    return Math.max(0, summaryMaxChars);
  }

  public void setSummaryMaxChars(int summaryMaxChars) {
    this.summaryMaxChars = summaryMaxChars;
  }

  public Duration getUserRetention() {
    return userRetention;
  }

  public void setUserRetention(Duration userRetention) {
    this.userRetention = userRetention;
  }

  public Duration getGuestRetention() {
    return guestRetention;
  }

  public void setGuestRetention(Duration guestRetention) {
    this.guestRetention = guestRetention;
  }

  public Duration getGuestCookieMaxAge() {
    return guestCookieMaxAge;
  }

  public void setGuestCookieMaxAge(Duration guestCookieMaxAge) {
    this.guestCookieMaxAge = guestCookieMaxAge;
  }

  public Duration getMaintenanceDelay() {
    return maintenanceDelay;
  }

  public void setMaintenanceDelay(Duration maintenanceDelay) {
    this.maintenanceDelay = maintenanceDelay;
  }

  public int getSummaryBatchSize() {
    return summaryBatchSize;
  }

  public void setSummaryBatchSize(int summaryBatchSize) {
    this.summaryBatchSize = summaryBatchSize;
  }

  public Duration getSummaryClaimTimeout() {
    return summaryClaimTimeout;
  }

  public void setSummaryClaimTimeout(Duration summaryClaimTimeout) {
    this.summaryClaimTimeout = summaryClaimTimeout;
  }

  public int getSummaryMaxAttempts() {
    return Math.max(1, summaryMaxAttempts);
  }

  public void setSummaryMaxAttempts(int summaryMaxAttempts) {
    this.summaryMaxAttempts = summaryMaxAttempts;
  }

  public boolean isPersistenceEnabled() {
    return persistenceEnabled;
  }

  public void setPersistenceEnabled(boolean persistenceEnabled) {
    this.persistenceEnabled = persistenceEnabled;
  }

  public boolean isMemoryEnabled() {
    return memoryEnabled;
  }

  public void setMemoryEnabled(boolean memoryEnabled) {
    this.memoryEnabled = memoryEnabled;
  }

  public String getGuestCookieName() {
    return guestCookieName;
  }

  public void setGuestCookieName(String guestCookieName) {
    this.guestCookieName = guestCookieName;
  }

  public String getGuestCookiePath() {
    return guestCookiePath;
  }

  public void setGuestCookiePath(String guestCookiePath) {
    this.guestCookiePath = guestCookiePath;
  }

  public String getGuestCookieDomain() {
    return guestCookieDomain;
  }

  public void setGuestCookieDomain(String guestCookieDomain) {
    this.guestCookieDomain = guestCookieDomain;
  }

  public boolean isGuestCookieSecure() {
    return guestCookieSecure;
  }

  public void setGuestCookieSecure(boolean guestCookieSecure) {
    this.guestCookieSecure = guestCookieSecure;
  }

  public boolean isGuestCookieHttpOnly() {
    return guestCookieHttpOnly;
  }

  public void setGuestCookieHttpOnly(boolean guestCookieHttpOnly) {
    this.guestCookieHttpOnly = guestCookieHttpOnly;
  }

  public String getGuestCookieSameSite() {
    return guestCookieSameSite;
  }

  public void setGuestCookieSameSite(String guestCookieSameSite) {
    this.guestCookieSameSite = guestCookieSameSite;
  }
}
