package com.example.moduleai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.agent")
public class AiAgentProperties {

  private boolean actionsEnabled;
  private String confirmationHmacSecret;
  private Duration proposalTtl = Duration.ofMinutes(5);
  private Duration resultRetention = Duration.ofHours(24);
  private Duration proposalRetention = Duration.ofDays(30);
  private Duration executingTimeout = Duration.ofMinutes(2);
  private Duration queryRateLimitWindow = Duration.ofMinutes(1);
  private int queryRateLimitPerUser = 30;
  private int queryRateLimitPerIp = 60;
  private Duration confirmationRateLimitWindow = Duration.ofMinutes(1);
  private int confirmationRateLimitPerUser = 10;
  private boolean queryRateLimitFailOpen = true;
  private Duration concurrencySlotTtl = Duration.ofMinutes(2);
  private boolean concurrencyFailOpen = false;
  private int maxConcurrentChatTurns = 2;

  public boolean isActionsEnabled() {
    return actionsEnabled;
  }

  public void setActionsEnabled(boolean actionsEnabled) {
    this.actionsEnabled = actionsEnabled;
  }

  public String getConfirmationHmacSecret() {
    return confirmationHmacSecret;
  }

  public void setConfirmationHmacSecret(String confirmationHmacSecret) {
    this.confirmationHmacSecret = confirmationHmacSecret;
  }

  public Duration getProposalTtl() {
    return proposalTtl;
  }

  public void setProposalTtl(Duration proposalTtl) {
    this.proposalTtl = proposalTtl;
  }

  public Duration getResultRetention() {
    return resultRetention;
  }

  public void setResultRetention(Duration resultRetention) {
    this.resultRetention = resultRetention;
  }

  public Duration getProposalRetention() {
    return proposalRetention;
  }

  public void setProposalRetention(Duration proposalRetention) {
    this.proposalRetention = proposalRetention;
  }

  public Duration getExecutingTimeout() {
    return executingTimeout;
  }

  public void setExecutingTimeout(Duration executingTimeout) {
    this.executingTimeout = executingTimeout;
  }

  public Duration getQueryRateLimitWindow() {
    return queryRateLimitWindow;
  }

  public void setQueryRateLimitWindow(Duration queryRateLimitWindow) {
    this.queryRateLimitWindow = queryRateLimitWindow;
  }

  public int getQueryRateLimitPerUser() {
    return queryRateLimitPerUser;
  }

  public void setQueryRateLimitPerUser(int queryRateLimitPerUser) {
    this.queryRateLimitPerUser = queryRateLimitPerUser;
  }

  public int getQueryRateLimitPerIp() {
    return queryRateLimitPerIp;
  }

  public void setQueryRateLimitPerIp(int queryRateLimitPerIp) {
    this.queryRateLimitPerIp = queryRateLimitPerIp;
  }

  public Duration getConfirmationRateLimitWindow() {
    return confirmationRateLimitWindow;
  }

  public void setConfirmationRateLimitWindow(Duration confirmationRateLimitWindow) {
    this.confirmationRateLimitWindow = confirmationRateLimitWindow;
  }

  public int getConfirmationRateLimitPerUser() {
    return confirmationRateLimitPerUser;
  }

  public void setConfirmationRateLimitPerUser(int confirmationRateLimitPerUser) {
    this.confirmationRateLimitPerUser = confirmationRateLimitPerUser;
  }

  public boolean isQueryRateLimitFailOpen() {
    return queryRateLimitFailOpen;
  }

  public void setQueryRateLimitFailOpen(boolean queryRateLimitFailOpen) {
    this.queryRateLimitFailOpen = queryRateLimitFailOpen;
  }

  public Duration getConcurrencySlotTtl() {
    return concurrencySlotTtl;
  }

  public void setConcurrencySlotTtl(Duration concurrencySlotTtl) {
    this.concurrencySlotTtl = concurrencySlotTtl;
  }

  public boolean isConcurrencyFailOpen() {
    return concurrencyFailOpen;
  }

  public void setConcurrencyFailOpen(boolean concurrencyFailOpen) {
    this.concurrencyFailOpen = concurrencyFailOpen;
  }

  public int getMaxConcurrentChatTurns() {
    return Math.max(1, maxConcurrentChatTurns);
  }

  public void setMaxConcurrentChatTurns(int maxConcurrentChatTurns) {
    this.maxConcurrentChatTurns = maxConcurrentChatTurns;
  }
}
