package com.example.appbootstrap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.Role;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
      "management.endpoint.health.show-details=always"
    })
@AutoConfigureMockMvc
class ActuatorSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldExposePublicHealthEndpoints() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

    mockMvc
        .perform(get("/actuator/health/ai"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").isNotEmpty())
        .andExpect(jsonPath("$.components.aiProvider.details.chatCircuitState").isNotEmpty())
        .andExpect(jsonPath("$.components.aiProvider.details.retrievalCircuitState").isNotEmpty());
  }

  @Test
  void shouldRequireAuthenticationForMetricsEndpoint() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldAllowAdminRoleForMetricsEndpoint() throws Exception {
    mockMvc
        .perform(
            get("/actuator/metrics")
                .with(
                    user(
                        new AppUserPrincipal(1L, "admin@example.com", "secret", Role.ADMIN, null))))
        .andExpect(status().isOk());
  }

  @Test
  void shouldExposePrometheusToAdminWhenEnabled() throws Exception {
    Assumptions.assumeFalse(Boolean.getBoolean("skipPrometheusRegistry"));
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .with(
                    user(
                        new AppUserPrincipal(1L, "admin@example.com", "secret", Role.ADMIN, null))))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/plain"));
  }
}
