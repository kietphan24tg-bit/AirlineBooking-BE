package com.example.moduleflight.integration.airlabs;

import com.example.common.exception.AppException;
import com.example.moduleflight.config.FlightImportProperties;
import com.example.moduleflight.integration.airlabs.dto.AirlabsScheduleItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AirlabsSchedulesClient {

  private static final String SCHEDULES_PATH = "/api/v9/schedules";

  private final FlightImportProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Autowired
  public AirlabsSchedulesClient(FlightImportProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newHttpClient());
  }

  AirlabsSchedulesClient(
      FlightImportProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  public List<AirlabsScheduleItem> fetchSchedules(String airlineIata, LocalDate fromDate) {
    URI endpoint = buildSchedulesUri(airlineIata, fromDate);
    int retries = Math.max(0, properties.maxRetries());
    int attempts = retries + 1;

    for (int attempt = 1; attempt <= attempts; attempt++) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(Math.max(1, properties.requestTimeoutSeconds())))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
          throw new AppException(
              "FLIGHT_IMPORT_API_ERROR",
              "AirLabs schedules API returned error status: " + response.statusCode(),
              502);
        }
        JsonNode root = objectMapper.readTree(response.body());
        AirlabsJsonSupport.throwIfErrorEnvelope(root);
        return AirlabsJsonSupport.readList(root, AirlabsScheduleItem.class, objectMapper);
      } catch (IOException ioException) {
        if (attempt == attempts) {
          throw new AppException(
              "FLIGHT_IMPORT_API_ERROR", "Unable to read schedule data from AirLabs API", 502);
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new AppException("FLIGHT_IMPORT_INTERRUPTED", "Flight import was interrupted", 500);
      }
    }

    throw new AppException("FLIGHT_IMPORT_API_ERROR", "AirLabs schedules request failed", 502);
  }

  private URI buildSchedulesUri(String airlineIata, LocalDate fromDate) {
    String baseUrl = sanitizeBaseUrl(properties.baseUrl());
    StringBuilder builder = new StringBuilder(baseUrl).append(SCHEDULES_PATH).append("?api_key=");
    builder.append(URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8));
    if (StringUtils.hasText(airlineIata)) {
      builder
          .append("&airline_iata=")
          .append(URLEncoder.encode(airlineIata.trim(), StandardCharsets.UTF_8));
    }
    if (fromDate != null) {
      builder.append("&date_from=").append(URLEncoder.encode(fromDate.toString(), StandardCharsets.UTF_8));
    }
    return URI.create(builder.toString());
  }

  private static String sanitizeBaseUrl(String configuredBaseUrl) {
    if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
      return "https://airlabs.co";
    }
    return configuredBaseUrl.endsWith("/")
        ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
        : configuredBaseUrl;
  }
}
