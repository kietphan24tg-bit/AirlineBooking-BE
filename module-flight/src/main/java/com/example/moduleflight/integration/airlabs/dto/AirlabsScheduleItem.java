package com.example.moduleflight.integration.airlabs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AirlabsScheduleItem(
    @JsonProperty("flight_iata") String flightIata,
    @JsonProperty("flight_icao") String flightIcao,
    @JsonProperty("airline_iata") String airlineIata,
    @JsonProperty("dep_iata") String depIata,
    @JsonProperty("arr_iata") String arrIata,
    @JsonProperty("dep_time") String depTime,
    @JsonProperty("arr_time") String arrTime,
    @JsonProperty("dep_terminal") String depTerminal,
    @JsonProperty("dep_gate") String depGate,
    @JsonProperty("arr_terminal") String arrTerminal,
    @JsonProperty("arr_gate") String arrGate,
    @JsonProperty("status") String status,
    @JsonProperty("aircraft_icao") String aircraftIcao) {}
