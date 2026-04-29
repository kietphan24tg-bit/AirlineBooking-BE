package com.example.appbootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class OpenApiCoverageIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @ParameterizedTest
  @MethodSource("documentedApiPaths")
  void shouldExposeApiPathInConfiguredOpenApiGroup(String group, String path) throws Exception {
    JsonNode spec = openApiSpec(group);

    assertThat(spec.path("paths").has(path)).isTrue();
  }

  @Test
  void shouldExposeResponseSchemasForRecentlyAddedBookingApis() throws Exception {
    JsonNode bookingsSpec = openApiSpec("bookings");
    JsonNode flightsSpec = openApiSpec("flights");
    JsonNode servicesSpec = openApiSpec("services");

    assertThat(schemaRef(bookingsSpec, "/api/bookings", "post"))
        .isEqualTo("#/components/schemas/CreateBookingResponse");
    assertThat(schemaRef(bookingsSpec, "/api/bookings/{bookingId}/seat-assignments", "put"))
        .isEqualTo("#/components/schemas/SeatAssignmentResponse");
    assertThat(schemaRef(bookingsSpec, "/api/bookings/{bookingId}/seat-assignments", "delete"))
        .isEqualTo("#/components/schemas/SeatAssignmentResponse");
    assertThat(schemaRef(bookingsSpec, "/api/bookings/{bookingId}/services", "put"))
        .isEqualTo("#/components/schemas/PassengerServiceSelectionResponse");
    assertThat(schemaRef(flightsSpec, "/api/flights/{flightId}/seats", "get"))
        .isEqualTo("#/components/schemas/FlightSeatMapResponse");
    assertThat(schemaRef(servicesSpec, "/api/services", "get"))
        .isEqualTo("#/components/schemas/AncillaryCatalogResponse");
  }

  private JsonNode openApiSpec(String group) throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs/{group}", group))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private static String schemaRef(JsonNode spec, String path, String method) {
    return spec.path("paths")
        .path(path)
        .path(method)
        .path("responses")
        .path("200")
        .path("content")
        .path("application/json")
        .path("schema")
        .path("$ref")
        .asText();
  }

  private static Stream<Arguments> documentedApiPaths() {
    return Stream.of(
        Arguments.of("identity", "/api/auth/register"),
        Arguments.of("identity", "/api/auth/login"),
        Arguments.of("identity", "/api/auth/refresh"),
        Arguments.of("identity", "/api/auth/logout"),
        Arguments.of("identity", "/api/auth/password-reset/request"),
        Arguments.of("identity", "/api/auth/password-reset/confirm"),
        Arguments.of("identity", "/api/users/me"),
        Arguments.of("identity", "/api/users/me/password"),
        Arguments.of("flights", "/api/flights/search"),
        Arguments.of("flights", "/api/flights/revalidate-fare"),
        Arguments.of("flights", "/api/flights/{flightId}/seats"),
        Arguments.of("flights", "/api/airports"),
        Arguments.of("flights", "/api/airports/picker"),
        Arguments.of("flights", "/api/airports/search"),
        Arguments.of("flights", "/api/airports/grouped-by-country"),
        Arguments.of("bookings", "/api/bookings"),
        Arguments.of("bookings", "/api/bookings/{bookingId}/seat-assignments"),
        Arguments.of("bookings", "/api/bookings/{bookingId}/services"),
        Arguments.of("services", "/api/services"),
        Arguments.of("payments", "/api/payment-methods"),
        Arguments.of("payments", "/api/payments"),
        Arguments.of("payments", "/api/payments/{paymentId}"),
        Arguments.of("payments", "/api/payments/webhooks/payos"),
        Arguments.of("payments", "/api/payments/webhooks/stripe"));
  }
}
