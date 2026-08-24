package com.example.moduleai.rollout;

import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.ai.agent.rollout")
public class AgentRolloutProperties {

  private AgentRolloutMode mode = AgentRolloutMode.OFF;
  private Set<String> enabledTools = new HashSet<>();
  private Set<Long> canaryUserIds = new HashSet<>();

  public AgentRolloutMode getMode() {
    return mode;
  }

  public void setMode(AgentRolloutMode mode) {
    this.mode = mode;
  }

  public Set<String> getEnabledTools() {
    return enabledTools;
  }

  public void setEnabledTools(Set<String> enabledTools) {
    this.enabledTools = enabledTools;
  }

  public Set<Long> getCanaryUserIds() {
    return canaryUserIds;
  }

  public void setCanaryUserIds(Set<Long> canaryUserIds) {
    this.canaryUserIds = canaryUserIds;
  }
}
