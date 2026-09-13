package com.example.moduleai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.agent.guardrail")
public class AiAgentGuardrailProperties {

  private int maxMessageLength = 4000;
  private int maxToolCallsPerTurn = 8;
  private int maxModelRoundsPerTurn = 8;
  private int maxStructuredFieldLength = 256;
  private int maxOutputStringLength = 1024;
  private int maxOutputCollectionItems = 20;
  private int maxOutputJsonBytes = 16384;

  public int getMaxMessageLength() {
    return maxMessageLength;
  }

  public void setMaxMessageLength(int maxMessageLength) {
    this.maxMessageLength = maxMessageLength;
  }

  public int getMaxToolCallsPerTurn() {
    return maxToolCallsPerTurn;
  }

  public void setMaxToolCallsPerTurn(int maxToolCallsPerTurn) {
    this.maxToolCallsPerTurn = maxToolCallsPerTurn;
  }

  public int getMaxModelRoundsPerTurn() {
    return maxModelRoundsPerTurn;
  }

  public void setMaxModelRoundsPerTurn(int maxModelRoundsPerTurn) {
    this.maxModelRoundsPerTurn = maxModelRoundsPerTurn;
  }

  public int getMaxStructuredFieldLength() {
    return maxStructuredFieldLength;
  }

  public void setMaxStructuredFieldLength(int maxStructuredFieldLength) {
    this.maxStructuredFieldLength = maxStructuredFieldLength;
  }

  public int getMaxOutputStringLength() {
    return maxOutputStringLength;
  }

  public void setMaxOutputStringLength(int maxOutputStringLength) {
    this.maxOutputStringLength = maxOutputStringLength;
  }

  public int getMaxOutputCollectionItems() {
    return maxOutputCollectionItems;
  }

  public void setMaxOutputCollectionItems(int maxOutputCollectionItems) {
    this.maxOutputCollectionItems = maxOutputCollectionItems;
  }

  public int getMaxOutputJsonBytes() {
    return maxOutputJsonBytes;
  }

  public void setMaxOutputJsonBytes(int maxOutputJsonBytes) {
    this.maxOutputJsonBytes = maxOutputJsonBytes;
  }
}
