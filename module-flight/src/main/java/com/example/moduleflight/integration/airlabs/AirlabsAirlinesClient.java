package com.example.moduleflight.integration.airlabs;

import com.example.common.exception.AppException;
import com.example.moduleflight.config.AirlineImportProperties;
import com.example.moduleflight.integration.airlabs.dto.AirlabsAirlineItem;
import com.example.moduleflight.integration.airlabs.dto.AirlabsCountryItem;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AirlabsAirlinesClient {

  private static final String AIRLINES_PATH = "/api/v9/airlines";
  private static final String COUNTRIES_PATH = "/api/v9/countries";

  private final AirlineImportProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Autowired
  public AirlabsAirlinesClient(AirlineImportProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newHttpClient());
  }

  AirlabsAirlinesClient(
      AirlineImportProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  public List<AirlabsAirlineItem> fetchAirlines() {
    return executeJsonList(
        buildAirlinesUri(),
        AirlabsAirlineItem.class,
        "Unable to read airline data from AirLabs API");
  }

  /**
   * Loads ISO2 {@code code} → {@code name} from the Countries DB (docs: airlabs.co/docs/countries)
   * using {@code _fields} to limit payload.
   */
  public Map<String, String> fetchCountryNamesByCode() {
    URI endpoint = buildCountriesUri();
    List<AirlabsCountryItem> rows =
        executeJsonList(
            endpoint, AirlabsCountryItem.class, "Unable to read countries data from AirLabs API");
    Map<String, String> map = new LinkedHashMap<>();
    for (AirlabsCountryItem row : rows) {
      if (row == null || row.code() == null || row.code().isBlank()) {
        continue;
      }
      String code = row.code().trim().toUpperCase(Locale.ROOT);
      if (code.length() != 2 || row.name() == null || row.name().isBlank()) {
        continue;
      }
      map.putIfAbsent(code, row.name().trim());
    }
    return map;
  }

  private <T> List<T> executeJsonList(URI endpoint, Class<T> elementClass, String ioErrorMessage) {
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
              "AIRLINE_IMPORT_API_ERROR",
              "AirLabs API returned error status: " + response.statusCode(),
              502);
        }
        JsonNode root = objectMapper.readTree(response.body());
        AirlabsJsonSupport.throwIfErrorEnvelope(root);
        return AirlabsJsonSupport.readList(root, elementClass, objectMapper);
      } catch (IOException ioException) {
        if (attempt == attempts) {
          throw new AppException("AIRLINE_IMPORT_API_ERROR", ioErrorMessage, 502);
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new AppException("AIRLINE_IMPORT_INTERRUPTED", "Airline import was interrupted", 500);
      }
    }

    throw new AppException("AIRLINE_IMPORT_API_ERROR", "AirLabs API request failed", 502);
  }

  private URI buildAirlinesUri() {
    String baseUrl = sanitizeBaseUrl(properties.baseUrl());
    StringBuilder builder = new StringBuilder(baseUrl).append(AIRLINES_PATH).append("?api_key=");
    builder.append(URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8));
    if (StringUtils.hasText(properties.fields())) {
      builder
          .append("&_fields=")
          .append(URLEncoder.encode(properties.fields().trim(), StandardCharsets.UTF_8));
    }
    return URI.create(builder.toString());
  }

  private URI buildCountriesUri() {
    String baseUrl = sanitizeBaseUrl(properties.baseUrl());
    StringBuilder builder = new StringBuilder(baseUrl).append(COUNTRIES_PATH).append("?api_key=");
    builder.append(URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8));
    if (StringUtils.hasText(properties.countriesFields())) {
      builder
          .append("&_fields=")
          .append(URLEncoder.encode(properties.countriesFields().trim(), StandardCharsets.UTF_8));
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
