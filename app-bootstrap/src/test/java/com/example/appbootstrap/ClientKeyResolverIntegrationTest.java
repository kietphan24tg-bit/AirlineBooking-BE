package com.example.appbootstrap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.moduleidentity.port.ClientKeyResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(ClientKeyResolverIntegrationTest.TestApiController.class)
class ClientKeyResolverIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldResolveClientIpFromForwardedHeaderWhenRemoteAddressIsTrustedProxy() throws Exception {
    mockMvc
        .perform(
            get("/client-key/current")
                .with(user("tester"))
                .header("X-Forwarded-For", "203.0.113.10, 10.0.0.15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clientKey").value("203.0.113.10"));
  }

  @Test
  void shouldIgnoreSpoofedForwardedHeaderWhenRemoteAddressIsNotTrustedProxy() throws Exception {
    mockMvc
        .perform(
            get("/client-key/current")
                .with(user("tester"))
                .with(request -> {
                  request.setRemoteAddr("198.51.100.25");
                  return request;
                })
                .header("X-Forwarded-For", "203.0.113.10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clientKey").value("198.51.100.25"));
  }

  @RestController
  @RequestMapping("/client-key")
  static class TestApiController {
    private final ClientKeyResolver clientKeyResolver;

    TestApiController(ClientKeyResolver clientKeyResolver) {
      this.clientKeyResolver = clientKeyResolver;
    }

    @GetMapping("/current")
    Map<String, Object> current() {
      return Map.of("clientKey", clientKeyResolver.resolveClientKey());
    }
  }
}
