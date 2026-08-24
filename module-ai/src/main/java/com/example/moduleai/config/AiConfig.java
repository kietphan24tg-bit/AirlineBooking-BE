package com.example.moduleai.config;

import com.example.moduleai.tool.catalog.ToolCatalog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  AiAgentProperties.class,
  AiProviderProperties.class,
  AiAgentGuardrailProperties.class
})
public class AiConfig {

  @Bean
  ToolCatalog toolCatalog() {
    return new ToolCatalog();
  }
}
