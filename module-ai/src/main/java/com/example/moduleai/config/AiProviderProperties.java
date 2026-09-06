package com.example.moduleai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.provider")
public class AiProviderProperties {

  private Duration timeout = Duration.ofSeconds(45);
  private int maxConcurrentCalls = 4;
  private int queueCapacity = 4;
  private int rateLimitPermits = 120;
  private Duration rateLimitRefreshPeriod = Duration.ofMinutes(1);
  private Duration rateLimitWait = Duration.ofMillis(100);
  private int circuitSlidingWindowSize = 10;
  private int circuitMinimumCalls = 10;
  private float circuitFailureRateThreshold = 50.0f;
  private Duration circuitOpenDuration = Duration.ofSeconds(30);
  private int circuitHalfOpenCalls = 3;

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public int getMaxConcurrentCalls() {
    return maxConcurrentCalls;
  }

  public void setMaxConcurrentCalls(int maxConcurrentCalls) {
    this.maxConcurrentCalls = maxConcurrentCalls;
  }

  public int getQueueCapacity() {
    return queueCapacity;
  }

  public void setQueueCapacity(int queueCapacity) {
    this.queueCapacity = queueCapacity;
  }

  public int getRateLimitPermits() {
    return rateLimitPermits;
  }

  public void setRateLimitPermits(int rateLimitPermits) {
    this.rateLimitPermits = rateLimitPermits;
  }

  public Duration getRateLimitRefreshPeriod() {
    return rateLimitRefreshPeriod;
  }

  public void setRateLimitRefreshPeriod(Duration rateLimitRefreshPeriod) {
    this.rateLimitRefreshPeriod = rateLimitRefreshPeriod;
  }

  public Duration getRateLimitWait() {
    return rateLimitWait;
  }

  public void setRateLimitWait(Duration rateLimitWait) {
    this.rateLimitWait = rateLimitWait;
  }

  public int getCircuitSlidingWindowSize() {
    return circuitSlidingWindowSize;
  }

  public void setCircuitSlidingWindowSize(int circuitSlidingWindowSize) {
    this.circuitSlidingWindowSize = circuitSlidingWindowSize;
  }

  public int getCircuitMinimumCalls() {
    return circuitMinimumCalls;
  }

  public void setCircuitMinimumCalls(int circuitMinimumCalls) {
    this.circuitMinimumCalls = circuitMinimumCalls;
  }

  public float getCircuitFailureRateThreshold() {
    return circuitFailureRateThreshold;
  }

  public void setCircuitFailureRateThreshold(float circuitFailureRateThreshold) {
    this.circuitFailureRateThreshold = circuitFailureRateThreshold;
  }

  public Duration getCircuitOpenDuration() {
    return circuitOpenDuration;
  }

  public void setCircuitOpenDuration(Duration circuitOpenDuration) {
    this.circuitOpenDuration = circuitOpenDuration;
  }

  public int getCircuitHalfOpenCalls() {
    return circuitHalfOpenCalls;
  }

  public void setCircuitHalfOpenCalls(int circuitHalfOpenCalls) {
    this.circuitHalfOpenCalls = circuitHalfOpenCalls;
  }
}
