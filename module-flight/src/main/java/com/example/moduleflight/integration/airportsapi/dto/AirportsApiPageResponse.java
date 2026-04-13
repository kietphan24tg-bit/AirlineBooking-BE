package com.example.moduleflight.integration.airportsapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AirportsApiPageResponse(
    List<AirportData> data,
    PageLinks links,
    PageMeta meta,
    List<IncludedResource> included,
    List<ApiError> errors) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AirportData(
      String id, String type, AirportAttributes attributes, AirportRelationships relationships) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AirportAttributes(
      String name,
      String code,
      String type,
      @JsonProperty("iata_code") String iataCode,
      @JsonProperty("icao_code") String icaoCode,
      String city,
      String country,
      @JsonProperty("time_zone") String timeZone) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AirportRelationships(RelationshipRef country, RelationshipRef region) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RelationshipRef(RelationshipData data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RelationshipData(String type, String id) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IncludedResource(String id, String type, IncludedAttributes attributes) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IncludedAttributes(String name, String code, String continent) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PageLinks(String next) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PageMeta(
      String path, @JsonProperty("next_cursor") String nextCursor, int perPage) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ApiError(String status, String title, String detail) {}
}
