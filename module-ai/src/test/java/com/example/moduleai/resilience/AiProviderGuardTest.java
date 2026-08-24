package com.example.moduleai.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.moduleai.config.AiProviderProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("rag-resilience")
class AiProviderGuardTest {

  private AiProviderGuard guard;

  @AfterEach
  void tearDown() {
    if (guard != null) {
      guard.close();
    }
  }

  @Test
  void usesConservativeCircuitDefaults() {
    AiProviderProperties properties = new AiProviderProperties();

    assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(25));
    assertThat(properties.getRateLimitPermits()).isEqualTo(120);
    assertThat(properties.getCircuitSlidingWindowSize()).isEqualTo(10);
    assertThat(properties.getCircuitMinimumCalls()).isEqualTo(10);
    assertThat(properties.getCircuitFailureRateThreshold()).isEqualTo(50.0f);
    assertThat(properties.getCircuitOpenDuration()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.getCircuitHalfOpenCalls()).isEqualTo(3);
  }

  @Test
  void rejectsCircuitMinimumCallsLargerThanWindow() {
    AiProviderProperties properties = permissiveProperties();
    properties.setCircuitSlidingWindowSize(10);
    properties.setCircuitMinimumCalls(11);

    assertThatThrownBy(
            () ->
                new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI provider circuit call window is invalid");
  }

  @Test
  void returnsOperationResult() {
    guard =
        new AiProviderGuard(
            permissiveProperties(), (io.micrometer.core.instrument.MeterRegistry) null);

    assertThat(guard.execute(() -> "answer")).isEqualTo("answer");
  }

  @Test
  void classifies429WithoutRetrying() {
    guard =
        new AiProviderGuard(
            permissiveProperties(), (io.micrometer.core.instrument.MeterRegistry) null);
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                guard.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new RuntimeException("429 RESOURCE_EXHAUSTED");
                    }))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> assertThat(exception.failure()).isEqualTo(AiProviderFailure.RATE_LIMITED));
    assertThat(attempts).hasValue(1);
  }

  @Test
  void classifies503AsUnavailable() {
    guard =
        new AiProviderGuard(
            permissiveProperties(), (io.micrometer.core.instrument.MeterRegistry) null);

    assertThatThrownBy(() -> guard.execute(() -> fail("503 service unavailable")))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> assertThat(exception.failure()).isEqualTo(AiProviderFailure.UNAVAILABLE));
  }

  @Test
  void classifies500AsUnavailable() {
    guard =
        new AiProviderGuard(
            permissiveProperties(), (io.micrometer.core.instrument.MeterRegistry) null);

    assertThatThrownBy(() -> guard.execute(() -> fail("500 internal server error")))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> assertThat(exception.failure()).isEqualTo(AiProviderFailure.UNAVAILABLE));
  }

  @Test
  void boundsSlowProviderCallWithTimeout() {
    AiProviderProperties properties = permissiveProperties();
    properties.setTimeout(Duration.ofMillis(50));
    guard = new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null);

    long startedAt = System.nanoTime();
    assertThatThrownBy(
            () ->
                guard.execute(
                    () -> {
                      try {
                        Thread.sleep(5_000);
                      } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                      }
                      return "late";
                    }))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> assertThat(exception.failure()).isEqualTo(AiProviderFailure.TIMEOUT));

    assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(1));
  }

  @Test
  void opensCircuitAndRejectsCallWithoutInvokingProvider() {
    AiProviderProperties properties = permissiveProperties();
    properties.setCircuitMinimumCalls(2);
    properties.setCircuitSlidingWindowSize(2);
    properties.setCircuitFailureRateThreshold(50.0f);
    guard = new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null);
    AtomicInteger attempts = new AtomicInteger();

    for (int index = 0; index < 2; index++) {
      assertThatThrownBy(
              () ->
                  guard.execute(
                      () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("503 unavailable");
                      }))
          .isInstanceOf(AiProviderException.class);
    }

    assertThat(guard.circuitState()).isEqualTo("OPEN");
    assertThatThrownBy(
            () ->
                guard.execute(
                    () -> {
                      attempts.incrementAndGet();
                      return "must not run";
                    }))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> assertThat(exception.failure()).isEqualTo(AiProviderFailure.CIRCUIT_OPEN));
    assertThat(attempts).hasValue(2);
  }

  @Test
  void opensAtFiveFailuresOutOfTenButNotAtFour() {
    AiProviderProperties properties = permissiveProperties();
    properties.setCircuitMinimumCalls(10);
    properties.setCircuitSlidingWindowSize(10);
    properties.setCircuitFailureRateThreshold(50.0f);
    guard = new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null);

    for (int index = 0; index < 6; index++) {
      assertThat(guard.execute(() -> "ok")).isEqualTo("ok");
    }
    for (int index = 0; index < 4; index++) {
      assertThatThrownBy(() -> guard.execute(() -> fail("503 unavailable")))
          .isInstanceOf(AiProviderException.class);
    }
    assertThat(guard.circuitState()).isEqualTo("CLOSED");

    assertThatThrownBy(() -> guard.execute(() -> fail("503 unavailable")))
        .isInstanceOf(AiProviderException.class);
    assertThat(guard.circuitState()).isEqualTo("OPEN");
  }

  @Test
  void closesCircuitAfterSuccessfulHalfOpenProbe() throws InterruptedException {
    AiProviderProperties properties = permissiveProperties();
    properties.setCircuitMinimumCalls(2);
    properties.setCircuitSlidingWindowSize(2);
    properties.setCircuitHalfOpenCalls(1);
    properties.setCircuitOpenDuration(Duration.ofMillis(20));
    guard = new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null);

    for (int index = 0; index < 2; index++) {
      assertThatThrownBy(() -> guard.execute(() -> fail("503 unavailable")))
          .isInstanceOf(AiProviderException.class);
    }
    Thread.sleep(30);

    assertThat(guard.execute(() -> "recovered")).isEqualTo("recovered");
    assertThat(guard.circuitState()).isEqualTo("CLOSED");
  }

  @Test
  void requiresThreeSuccessfulProbesWithProductionHalfOpenSettings() throws InterruptedException {
    AiProviderProperties properties = permissiveProperties();
    properties.setCircuitMinimumCalls(10);
    properties.setCircuitSlidingWindowSize(10);
    properties.setCircuitHalfOpenCalls(3);
    properties.setCircuitOpenDuration(Duration.ofMillis(20));
    guard = new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null);

    for (int index = 0; index < 10; index++) {
      assertThatThrownBy(() -> guard.execute(() -> fail("503 unavailable")))
          .isInstanceOf(AiProviderException.class);
    }
    Thread.sleep(30);

    assertThat(guard.execute(() -> "probe-1")).isEqualTo("probe-1");
    assertThat(guard.execute(() -> "probe-2")).isEqualTo("probe-2");
    assertThat(guard.circuitState()).isEqualTo("HALF_OPEN");
    assertThat(guard.execute(() -> "probe-3")).isEqualTo("probe-3");
    assertThat(guard.circuitState()).isEqualTo("CLOSED");
  }

  @Test
  void rejectsCallsBeyondConfiguredRateWithoutInvokingProvider() {
    AiProviderProperties properties = permissiveProperties();
    properties.setRateLimitPermits(1);
    properties.setRateLimitRefreshPeriod(Duration.ofMinutes(1));
    properties.setRateLimitWait(Duration.ZERO);
    guard = new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null);
    AtomicInteger attempts = new AtomicInteger();

    assertThat(guard.execute(attempts::incrementAndGet)).isEqualTo(1);
    assertThatThrownBy(() -> guard.execute(attempts::incrementAndGet))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> assertThat(exception.failure()).isEqualTo(AiProviderFailure.RATE_LIMITED));
    assertThat(attempts).hasValue(1);
  }

  @Test
  void keepsRetrievalAndChatRateLimitsIndependent() {
    AiProviderProperties properties = permissiveProperties();
    properties.setRateLimitPermits(1);
    properties.setRateLimitRefreshPeriod(Duration.ofMinutes(1));
    guard = new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null);

    assertThat(guard.executeRetrieval(() -> "context")).isEqualTo("context");
    assertThat(guard.execute(() -> "answer")).isEqualTo("answer");
    assertThatThrownBy(() -> guard.executeRetrieval(() -> "second context"))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> assertThat(exception.failure()).isEqualTo(AiProviderFailure.RATE_LIMITED));
  }

  @Test
  void boundsSlowRetrievalWithSameTimeoutBudget() {
    AiProviderProperties properties = permissiveProperties();
    properties.setTimeout(Duration.ofMillis(50));
    guard = new AiProviderGuard(properties, (io.micrometer.core.instrument.MeterRegistry) null);

    assertThatThrownBy(
            () ->
                guard.executeRetrieval(
                    () -> {
                      try {
                        Thread.sleep(5_000);
                      } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                      }
                      return "late context";
                    }))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            exception -> assertThat(exception.failure()).isEqualTo(AiProviderFailure.TIMEOUT));
  }

  @Test
  void rejectsLoadBeyondWorkersAndSharedQueue() throws Exception {
    AiProviderProperties properties = permissiveProperties();
    properties.setMaxConcurrentCalls(2);
    properties.setQueueCapacity(2);
    properties.setTimeout(Duration.ofSeconds(5));
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    guard = new AiProviderGuard(properties, meterRegistry);
    CountDownLatch releaseOperations = new CountDownLatch(1);
    CountDownLatch workersStarted = new CountDownLatch(2);
    ExecutorService callers = Executors.newFixedThreadPool(4);
    List<Future<String>> accepted = new ArrayList<>();

    try {
      for (int index = 0; index < 4; index++) {
        accepted.add(
            callers.submit(
                () ->
                    guard.execute(
                        () -> {
                          workersStarted.countDown();
                          await(releaseOperations);
                          return "done";
                        })));
      }
      assertThat(workersStarted.await(2, TimeUnit.SECONDS)).isTrue();
      awaitQueueSize(meterRegistry, 2.0);

      assertThatThrownBy(() -> guard.execute(() -> "overflow"))
          .isInstanceOfSatisfying(
              AiProviderException.class,
              exception ->
                  assertThat(exception.failure()).isEqualTo(AiProviderFailure.CAPACITY_EXHAUSTED));

      releaseOperations.countDown();
      for (Future<String> future : accepted) {
        assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo("done");
      }
    } finally {
      releaseOperations.countDown();
      callers.shutdownNow();
    }
  }

  @Test
  void publishesCallAndCircuitMetrics() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    guard = new AiProviderGuard(permissiveProperties(), meterRegistry);

    guard.execute(() -> "answer");
    assertThatThrownBy(() -> guard.execute(() -> fail("provider error")))
        .isInstanceOf(AiProviderException.class);

    assertThat(
            meterRegistry
                .get("app.ai.provider.calls")
                .tag("provider", "all")
                .tag("outcome", "success")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get("app.ai.provider.calls")
                .tag("provider", "all")
                .tag("outcome", "failure")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.find("app.ai.provider.circuit.state").gauges()).hasSize(2);
    assertThat(meterRegistry.find("app.ai.provider.duration").timers()).hasSize(2);
    assertThat(meterRegistry.find("app.ai.provider.failures").counters()).hasSize(12);
    assertThat(meterRegistry.find("app.ai.provider.executor.active").gauge()).isNotNull();
    assertThat(meterRegistry.find("app.ai.provider.executor.queue.size").gauge()).isNotNull();
  }

  private void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Test operation interrupted", exception);
    }
  }

  private void awaitQueueSize(SimpleMeterRegistry meterRegistry, double expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (System.nanoTime() < deadline) {
      double size = meterRegistry.get("app.ai.provider.executor.queue.size").gauge().value();
      if (size == expected) {
        return;
      }
      Thread.sleep(10);
    }
    assertThat(meterRegistry.get("app.ai.provider.executor.queue.size").gauge().value())
        .isEqualTo(expected);
  }

  private AiProviderProperties permissiveProperties() {
    AiProviderProperties properties = new AiProviderProperties();
    properties.setRateLimitPermits(100);
    properties.setRateLimitRefreshPeriod(Duration.ofSeconds(1));
    properties.setRateLimitWait(Duration.ZERO);
    properties.setTimeout(Duration.ofSeconds(2));
    return properties;
  }

  private String fail(String message) {
    throw new RuntimeException(message);
  }
}
