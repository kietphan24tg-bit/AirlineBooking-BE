package com.example.moduleflight.integration.airlabs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AirlabsCountryItem(String code, String name) {}
