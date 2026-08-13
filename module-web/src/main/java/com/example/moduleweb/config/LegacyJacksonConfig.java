package com.example.moduleweb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LegacyJacksonConfig {

  @Bean
  ObjectMapper legacyObjectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }
}
