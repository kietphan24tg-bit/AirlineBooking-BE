package com.example.moduleai.resilience;

import com.example.moduleai.config.AiProviderProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiProviderGuard {

  private static final String CHAT_PROVIDER = "chat-model";
  private static final String RETRIEVAL_PROVIDER = "retrieval-model";
  private static final String SHARED_GUARD = "ai-provider";

  private final CircuitBreaker chatCircuitBreaker;
  private final CircuitBreaker retrievalCircuitBreaker;
  private final RateLimiter chatRateLimiter;
  private final RateLimiter retrievalRateLimiter;
  private final Bulkhead bulkhead;
  private final TimeLimiter timeLimiter;
  private final ThreadPoolExecutor executor;
  private final Counter successCounter;
  private final Counter failureCounter;
  private final Timer chatDuration;
  private final Timer retrievalDuration;
  private final Map<AiProviderFailure, Counter> chatFailureCounters;
  private final Map<AiProviderFailure, Counter> retrievalFailureCounters;

  @Autowired
  public AiProviderGuard(
      AiProviderProperties properties, ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this(properties, meterRegistryProvider.getIfAvailable());
  }

  AiProviderGuard(AiProviderProperties properties, MeterRegistry meterRegistry) {
    validate(properties);
    chatCircuitBreaker = createCircuitBreaker(CHAT_PROVIDER, properties);
    retrievalCircuitBreaker = createCircuitBreaker(RETRIEVAL_PROVIDER, properties);
    chatRateLimiter = createRateLimiter(CHAT_PROVIDER, properties);
    retrievalRateLimiter = createRateLimiter(RETRIEVAL_PROVIDER, properties);
    bulkhead = createBulkhead(properties);
    timeLimiter = createTimeLimiter(properties);
    executor = createExecutor(properties);
    successCounter = counter(meterRegistry, "success");
    failureCounter = counter(meterRegistry, "failure");
    chatDuration = timer(meterRegistry, CHAT_PROVIDER);
    retrievalDuration = timer(meterRegistry, RETRIEVAL_PROVIDER);
    chatFailureCounters = failureCounters(meterRegistry, CHAT_PROVIDER);
    retrievalFailureCounters = failureCounters(meterRegistry, RETRIEVAL_PROVIDER);
    registerCircuitGauge(meterRegistry, chatCircuitBreaker, CHAT_PROVIDER);
    registerCircuitGauge(meterRegistry, retrievalCircuitBreaker, RETRIEVAL_PROVIDER);
    registerExecutorGauges(meterRegistry);
  }

  public <T> T execute(Supplier<T> operation) {
    return execute(
        operation, chatCircuitBreaker, chatRateLimiter, chatDuration, chatFailureCounters);
  }

  public <T> T executeRetrieval(Supplier<T> operation) {
    return execute(
        operation,
        retrievalCircuitBreaker,
        retrievalRateLimiter,
        retrievalDuration,
        retrievalFailureCounters);
  }

  private <T> T execute(
      Supplier<T> operation,
      CircuitBreaker circuitBreaker,
      RateLimiter rateLimiter,
      Timer durationTimer,
      Map<AiProviderFailure, Counter> providerFailureCounters) {
    long startedAt = System.nanoTime();
    Supplier<T> bulkheadProtected = Bulkhead.decorateSupplier(bulkhead, operation);
    Supplier<T> timed = () -> executeTimed(bulkheadProtected);
    Supplier<T> circuitProtected = CircuitBreaker.decorateSupplier(circuitBreaker, timed);
    Supplier<T> guarded = RateLimiter.decorateSupplier(rateLimiter, circuitProtected);

    try {
      T result = guarded.get();
      increment(successCounter);
      return result;
    } catch (RuntimeException exception) {
      increment(failureCounter);
      AiProviderException classified = classify(exception);
      increment(providerFailureCounters.get(classified.failure()));
      throw classified;
    } finally {
      record(durationTimer, startedAt);
    }
  }

  public boolean isAvailable() {
    return isCircuitAvailable(chatCircuitBreaker) && isCircuitAvailable(retrievalCircuitBreaker);
  }

  public String circuitState() {
    return chatCircuitBreaker.getState().name();
  }

  public String retrievalCircuitState() {
    return retrievalCircuitBreaker.getState().name();
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }

  private <T> T executeTimed(Supplier<T> operation) {
    try {
      return timeLimiter.executeFutureSupplier(() -> executor.submit(operation::get));
    } catch (TimeoutException exception) {
      throw new AiProviderException(AiProviderFailure.TIMEOUT, exception);
    } catch (RejectedExecutionException exception) {
      throw new AiProviderException(AiProviderFailure.CAPACITY_EXHAUSTED, exception);
    } catch (ExecutionException exception) {
      throw propagate(exception.getCause());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AiProviderException(AiProviderFailure.UNAVAILABLE, exception);
    } catch (Exception exception) {
      throw propagate(exception);
    }
  }

  private RuntimeException propagate(Throwable throwable) {
    if (throwable instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new AiProviderException(AiProviderFailure.ERROR, throwable);
  }

  private AiProviderException classify(RuntimeException exception) {
    if (exception instanceof AiProviderException providerException) {
      return providerException;
    }
    if (exception instanceof RequestNotPermitted) {
      return new AiProviderException(AiProviderFailure.RATE_LIMITED, exception);
    }
    if (exception instanceof CallNotPermittedException) {
      return new AiProviderException(AiProviderFailure.CIRCUIT_OPEN, exception);
    }
    if (exception instanceof BulkheadFullException
        || exception instanceof RejectedExecutionException) {
      return new AiProviderException(AiProviderFailure.CAPACITY_EXHAUSTED, exception);
    }

    String errorText = exceptionChainText(exception);
    if (containsAny(errorText, "429", "resource_exhausted", "too many requests", "rate limit")) {
      return new AiProviderException(AiProviderFailure.RATE_LIMITED, exception);
    }
    if (containsAny(errorText, "500", "503", "unavailable", "service unavailable")) {
      return new AiProviderException(AiProviderFailure.UNAVAILABLE, exception);
    }
    return new AiProviderException(AiProviderFailure.ERROR, exception);
  }

  private String exceptionChainText(Throwable throwable) {
    StringBuilder result = new StringBuilder();
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null) {
        result.append(' ').append(current.getMessage().toLowerCase(Locale.ROOT));
      }
      current = current.getCause();
    }
    return result.toString();
  }

  private boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private boolean isCircuitAvailable(CircuitBreaker breaker) {
    CircuitBreaker.State state = breaker.getState();
    return state != CircuitBreaker.State.OPEN && state != CircuitBreaker.State.FORCED_OPEN;
  }

  private CircuitBreaker createCircuitBreaker(
      String providerName, AiProviderProperties properties) {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(properties.getCircuitFailureRateThreshold())
            .slidingWindowSize(properties.getCircuitSlidingWindowSize())
            .minimumNumberOfCalls(properties.getCircuitMinimumCalls())
            .permittedNumberOfCallsInHalfOpenState(properties.getCircuitHalfOpenCalls())
            .waitDurationInOpenState(properties.getCircuitOpenDuration())
            .ignoreExceptions(BulkheadFullException.class)
            .build();
    return CircuitBreaker.of(providerName, config);
  }

  private RateLimiter createRateLimiter(String providerName, AiProviderProperties properties) {
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(properties.getRateLimitPermits())
            .limitRefreshPeriod(properties.getRateLimitRefreshPeriod())
            .timeoutDuration(properties.getRateLimitWait())
            .build();
    return RateLimiter.of(providerName, config);
  }

  private Bulkhead createBulkhead(AiProviderProperties properties) {
    BulkheadConfig config =
        BulkheadConfig.custom()
            .maxConcurrentCalls(properties.getMaxConcurrentCalls())
            .maxWaitDuration(java.time.Duration.ZERO)
            .build();
    return Bulkhead.of(SHARED_GUARD, config);
  }

  private TimeLimiter createTimeLimiter(AiProviderProperties properties) {
    TimeLimiterConfig config =
        TimeLimiterConfig.custom()
            .timeoutDuration(properties.getTimeout())
            .cancelRunningFuture(true)
            .build();
    return TimeLimiter.of(SHARED_GUARD, config);
  }

  private ThreadPoolExecutor createExecutor(AiProviderProperties properties) {
    AtomicInteger threadNumber = new AtomicInteger();
    return new ThreadPoolExecutor(
        properties.getMaxConcurrentCalls(),
        properties.getMaxConcurrentCalls(),
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(properties.getQueueCapacity()),
        runnable -> {
          Thread thread = new Thread(runnable, "ai-provider-" + threadNumber.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        },
        new ThreadPoolExecutor.AbortPolicy());
  }

  private Counter counter(MeterRegistry meterRegistry, String outcome) {
    if (meterRegistry == null) {
      return null;
    }
    return Counter.builder("app.ai.provider.calls")
        .tag("provider", "all")
        .tag("outcome", outcome)
        .register(meterRegistry);
  }

  private Timer timer(MeterRegistry meterRegistry, String providerName) {
    if (meterRegistry == null) {
      return null;
    }
    return Timer.builder("app.ai.provider.duration")
        .tag("provider", providerName)
        .register(meterRegistry);
  }

  private Map<AiProviderFailure, Counter> failureCounters(
      MeterRegistry meterRegistry, String providerName) {
    Map<AiProviderFailure, Counter> counters = new EnumMap<>(AiProviderFailure.class);
    if (meterRegistry == null) {
      return counters;
    }
    for (AiProviderFailure failure : AiProviderFailure.values()) {
      counters.put(
          failure,
          Counter.builder("app.ai.provider.failures")
              .tag("provider", providerName)
              .tag("reason", failure.name())
              .register(meterRegistry));
    }
    return counters;
  }

  private void registerExecutorGauges(MeterRegistry meterRegistry) {
    if (meterRegistry == null) {
      return;
    }
    Gauge.builder("app.ai.provider.executor.active", executor, ThreadPoolExecutor::getActiveCount)
        .register(meterRegistry);
    Gauge.builder("app.ai.provider.executor.queue.size", executor, value -> value.getQueue().size())
        .register(meterRegistry);
    Gauge.builder(
            "app.ai.provider.executor.queue.remaining",
            executor,
            value -> value.getQueue().remainingCapacity())
        .register(meterRegistry);
  }

  private void registerCircuitGauge(
      MeterRegistry meterRegistry, CircuitBreaker circuitBreaker, String providerName) {
    if (meterRegistry == null) {
      return;
    }
    Gauge.builder("app.ai.provider.circuit.state", circuitBreaker, this::stateValue)
        .tag("provider", providerName)
        .register(meterRegistry);
  }

  private double stateValue(CircuitBreaker breaker) {
    return switch (breaker.getState()) {
      case CLOSED -> 0.0;
      case HALF_OPEN -> 0.5;
      case OPEN, FORCED_OPEN -> 1.0;
      case DISABLED, METRICS_ONLY -> -1.0;
    };
  }

  private void increment(Counter counter) {
    if (counter != null) {
      counter.increment();
    }
  }

  private void record(Timer timer, long startedAtNanos) {
    if (timer != null) {
      timer.record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }
  }

  private void validate(AiProviderProperties properties) {
    if (properties.getTimeout().isNegative() || properties.getTimeout().isZero()) {
      throw new IllegalArgumentException("AI provider timeout must be positive");
    }
    if (properties.getMaxConcurrentCalls() < 1 || properties.getQueueCapacity() < 1) {
      throw new IllegalArgumentException("AI provider capacity must be positive");
    }
    if (properties.getRateLimitPermits() < 1) {
      throw new IllegalArgumentException("AI provider rate limit permits must be positive");
    }
    if (properties.getRateLimitRefreshPeriod().isNegative()
        || properties.getRateLimitRefreshPeriod().isZero()
        || properties.getRateLimitWait().isNegative()) {
      throw new IllegalArgumentException("AI provider rate limit durations are invalid");
    }
    if (properties.getCircuitSlidingWindowSize() < 1
        || properties.getCircuitMinimumCalls() < 1
        || properties.getCircuitMinimumCalls() > properties.getCircuitSlidingWindowSize()) {
      throw new IllegalArgumentException("AI provider circuit call window is invalid");
    }
    if (properties.getCircuitFailureRateThreshold() <= 0.0f
        || properties.getCircuitFailureRateThreshold() > 100.0f) {
      throw new IllegalArgumentException("AI provider circuit failure threshold is invalid");
    }
    if (properties.getCircuitOpenDuration().isNegative()
        || properties.getCircuitOpenDuration().isZero()
        || properties.getCircuitHalfOpenCalls() < 1) {
      throw new IllegalArgumentException("AI provider circuit recovery settings are invalid");
    }
  }
}
