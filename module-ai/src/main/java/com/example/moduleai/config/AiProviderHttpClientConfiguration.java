package com.example.moduleai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiProviderHttpClientConfiguration {

  @Bean
  OpenAiHttpClientBuilderCustomizer aiProviderHttpClientTimeout(
      AiProviderProperties providerProperties) {
    return builder -> builder.timeout(providerProperties.getTimeout());
  }

  @Bean
  @Conditional(GoogleHttpClientCondition.class)
  Client aiProviderGoogleGenAiClient(
      AiProviderProperties providerProperties,
      org.springframework.core.env.Environment environment) {
    int timeoutMillis = Math.toIntExact(providerProperties.getTimeout().toMillis());
    return Client.builder()
        .apiKey(environment.getRequiredProperty("spring.ai.google.genai.api-key"))
        .httpOptions(
            HttpOptions.builder()
                .timeout(timeoutMillis)
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build())
        .build();
  }

  static final class GoogleHttpClientCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
        ConditionContext context, AnnotatedTypeMetadata metadata) {
      boolean enabled =
          context
              .getEnvironment()
              .getProperty("app.ai.provider.google-http-timeout-enabled", Boolean.class, true);
      String apiKey =
          context.getEnvironment().getProperty("spring.ai.google.genai.api-key", "").trim();
      if (enabled && !apiKey.isEmpty()) {
        return ConditionOutcome.match(
            "Google HTTP timeout is enabled and an API key is configured");
      }
      if (!enabled) {
        return ConditionOutcome.noMatch("Google HTTP timeout is disabled");
      }
      return ConditionOutcome.noMatch("Google API key is not configured");
    }
  }
}
