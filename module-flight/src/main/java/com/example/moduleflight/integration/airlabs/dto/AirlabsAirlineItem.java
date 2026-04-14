package com.example.moduleflight.integration.airlabs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AirlabsAirlineItem(
    String name,
    @JsonProperty("iata_code") String iataCode,
    @JsonProperty("icao_code") String icaoCode,
    @JsonProperty("country_code") String countryCode) {}
