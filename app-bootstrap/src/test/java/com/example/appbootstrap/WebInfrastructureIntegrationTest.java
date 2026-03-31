package com.example.appbootstrap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.appbootstrap.api.ApiSuccessMessage;
import com.example.modulebooking.exception.BookingNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WebInfrastructureIntegrationTest.TestApiController.class)
class WebInfrastructureIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldWrapSuccessfulResponseWithDefaultMessage() throws Exception {
    mockMvc
        .perform(get("/test/success").with(user("tester")))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.value").value("ok"))
        .andExpect(jsonPath("$.message").value("Operation successful"));
  }

  @Test
  void shouldWrapSuccessfulResponseWithCustomMessage() throws Exception {
    mockMvc
        .perform(get("/test/success-with-message").with(user("tester")))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.value").value("ok"))
        .andExpect(jsonPath("$.message").value("Custom success message"));
  }

  @Test
  void shouldReturnStandardBusinessErrorResponse() throws Exception {
    mockMvc
        .perform(get("/test/business-error").with(user("tester")))
        .andExpect(status().isNotFound())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("Booking not found"))
        .andExpect(jsonPath("$.statusCode").value(404))
        .andExpect(jsonPath("$.path").value("/test/business-error"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void shouldReturnStandardValidationErrorResponse() throws Exception {
    mockMvc
        .perform(
            post("/test/validation-error")
                .with(user("tester"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value("Validation failed"))
        .andExpect(jsonPath("$.error.details.name").isNotEmpty())
        .andExpect(jsonPath("$.statusCode").value(400))
        .andExpect(jsonPath("$.path").value("/test/validation-error"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void shouldReturnStandardUnhandledSystemErrorResponse() throws Exception {
    mockMvc
        .perform(get("/test/runtime-error").with(user("tester")))
        .andExpect(status().isInternalServerError())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
        .andExpect(jsonPath("$.error.message").value("An unexpected system error occurred"))
        .andExpect(jsonPath("$.statusCode").value(500))
        .andExpect(jsonPath("$.path").value("/test/runtime-error"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void shouldExposeHealthEndpointWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void shouldProtectNonWhitelistedEndpoints() throws Exception {
    mockMvc.perform(get("/test/protected")).andExpect(status().isUnauthorized());
  }

  @RestController
  @Validated
  @RequestMapping("/test")
  static class TestApiController {

    @GetMapping("/success")
    Map<String, Object> success() {
      return Map.of("value", "ok");
    }

    @GetMapping("/success-with-message")
    @ApiSuccessMessage("Custom success message")
    Map<String, Object> successWithMessage() {
      return Map.of("value", "ok");
    }

    @GetMapping("/business-error")
    Map<String, Object> businessError() {
      throw new BookingNotFoundException(999L);
    }

    @PostMapping("/validation-error")
    Map<String, Object> validationError(@Valid @RequestBody TestRequest request) {
      return Map.of("name", request.name());
    }

    @GetMapping("/runtime-error")
    Map<String, Object> runtimeError() {
      throw new IllegalStateException("Unexpected failure");
    }

    @GetMapping("/protected")
    Map<String, Object> protectedEndpoint(
        @RequestParam(required = false) @NotBlank String ignored) {
      return Map.of("secured", true);
    }
  }

  record TestRequest(@NotBlank(message = "name must not be blank") String name) {}
}
