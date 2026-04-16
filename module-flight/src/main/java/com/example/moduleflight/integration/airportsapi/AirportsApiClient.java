package com.example.moduleflight.integration.airportsapi;

import com.example.common.exception.AppException;
import com.example.moduleflight.config.AirportImportProperties;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AirportsApiClient {

  private static final String AIRPORTS_PATH = "/api/airports";
  private static final String PAGE_SIZE_PARAM = "page%5Bsize%5D";
  private static final String FILTER_TYPE_PARAM = "filter%5Btype%5D";
  private static final String PAGE_CURSOR_PARAM = "page%5Bcursor%5D";

  private final AirportImportProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Autowired
  public AirportsApiClient(AirportImportProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newHttpClient());
  }

  AirportsApiClient(
      AirportImportProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  public AirportsApiPageResponse fetchPage(String cursor) {
    URI endpoint = buildEndpoint(cursor);
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
        int statusCode = response.statusCode();
        if (statusCode >= 400) {
          boolean transientStatus = statusCode == 429 || (statusCode >= 500 && statusCode < 600);
          if (transientStatus && attempt < attempts) {
            continue;
          }
          throw new AppException(
              "AIRPORT_IMPORT_API_ERROR",
              "Airport API returned error status: " + statusCode + ", body: " + response.body(),
              502);
        }
        return objectMapper.readValue(response.body(), AirportsApiPageResponse.class);
      } catch (IOException ioException) {
        if (attempt == attempts) {
          throw new AppException(
              "AIRPORT_IMPORT_API_ERROR", "Unable to read airport data from external API", 502);
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new AppException("AIRPORT_IMPORT_INTERRUPTED", "Airport import was interrupted", 500);
      }
    }

    throw new AppException("AIRPORT_IMPORT_API_ERROR", "Airport API request failed", 502);
  }

  private URI buildEndpoint(String cursor) {
    String baseUrl = sanitizeBaseUrl(properties.baseUrl());
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(baseUrl + AIRPORTS_PATH)
            .queryParam(PAGE_SIZE_PARAM, Math.max(1, properties.pageSize()))
            .queryParam("sort", "name");

    if (StringUtils.hasText(properties.include())) {
      builder.queryParam("include", properties.include());
    }
    if (StringUtils.hasText(properties.allowedTypes())) {
      builder.queryParam(FILTER_TYPE_PARAM, properties.allowedTypes());
    }

    if (StringUtils.hasText(cursor)) {
      builder.queryParam(PAGE_CURSOR_PARAM, cursor);
    }
    return builder.build(true).toUri();
  }

  private String sanitizeBaseUrl(String configuredBaseUrl) {
    if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
      return "https://airportsapi.com";
    }
    return configuredBaseUrl.endsWith("/")
        ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
        : configuredBaseUrl;
  }
}
