package com.example.moduleflight.application;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FareClassRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightFareGenerationService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightFareGenerationService.class);
  private static final List<String> SUPPORTED_FARE_CODES =
      List.of("ECONOMY", "PREMIUM", "BUSINESS", "FIRST");

  private final FareClassRepository fareClassRepository;
  private final FlightFareRepository flightFareRepository;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbcTemplate;
  private final FlightInventoryMetadataService flightInventoryMetadataService;
  private final RouteBasedFarePricingService routeBasedFarePricingService;

  public FlightFareGenerationService(
      FareClassRepository fareClassRepository,
      FlightFareRepository flightFareRepository,
      ObjectMapper objectMapper,
      JdbcTemplate jdbcTemplate,
      FlightInventoryMetadataService flightInventoryMetadataService,
      RouteBasedFarePricingService routeBasedFarePricingService) {
    this.fareClassRepository = fareClassRepository;
    this.flightFareRepository = flightFareRepository;
    this.objectMapper = objectMapper;
    this.jdbcTemplate = jdbcTemplate;
    this.flightInventoryMetadataService = flightInventoryMetadataService;
    this.routeBasedFarePricingService = routeBasedFarePricingService;
  }

  @Transactional
  public void generateFaresForFlight(FlightEntity flight) {
    if (flight == null || flight.getId() == null) {
      return;
    }
    flightInventoryMetadataService.repairMetadataForSaleability();

    List<FareClassEntity> fares = fareClassRepository.findByCodeIn(SUPPORTED_FARE_CODES);
    if (fares.isEmpty()) {
      LOG.warn("Skip flight fare generation: no fare classes configured.");
      return;
    }
    Map<String, FareClassEntity> fareByCode = new HashMap<>();
    for (FareClassEntity fareClass : fares) {
      fareByCode.put(normalize(fareClass.getCode()), fareClass);
    }
    OffsetDateTime now = OffsetDateTime.now();
    List<FlightFareEntity> toSave = new ArrayList<>();
    Set<String> supportedSeatClassCodes = supportedSeatClassCodes(flight);

    for (String fareCode : SUPPORTED_FARE_CODES) {
      FareClassEntity fareClass = fareByCode.get(fareCode);
      if (fareClass == null) {
        LOG.warn("Skip fare generation for {} because fare class is not configured.", fareCode);
        continue;
      }
      FlightFareEntity entity =
          flightFareRepository
              .findByFlightAndFareClass(flight, fareClass)
              .orElseGet(FlightFareEntity::new);
      String canonicalSeatClassCode =
          flightInventoryMetadataService.canonicalSeatClassCode(fareClass.getCabinClass());
      if (!StringUtils.hasText(canonicalSeatClassCode)
          || !supportedSeatClassCodes.contains(canonicalSeatClassCode)) {
        if (entity.getId() != null) {
          entity.setStatus("SOLD_OUT");
          entity.setUpdatedAt(now);
          toSave.add(entity);
        }
        continue;
      }
      var pricing = routeBasedFarePricingService.priceFare(flight, fareClass);
      if (pricing.isEmpty()) {
        if (entity.getId() != null) {
          entity.setStatus("SOLD_OUT");
          entity.setUpdatedAt(now);
          toSave.add(entity);
        }
        continue;
      }
      String taxBreakdownJson = writeTaxBreakdown(pricing.get().taxBreakdown());

      entity.setFlight(flight);
      entity.setFareClass(fareClass);
      entity.setBasePrice(pricing.get().baseFare());
      entity.setTaxBreakdown(taxBreakdownJson);
      entity.setTotalTaxAmount(pricing.get().totalTaxAmount());
      entity.setTotalPrice(pricing.get().totalPrice());
      if (entity.getId() == null || entity.getStatus() == null) {
        entity.setStatus("AVAILABLE");
      }
      entity.setUpdatedAt(now);
      toSave.add(entity);
    }

    flightFareRepository.saveAll(toSave);
  }

  private Set<String> supportedSeatClassCodes(FlightEntity flight) {
    if (flight == null || flight.getAircraft() == null || flight.getAircraft().getId() == null) {
      return Set.of();
    }
    List<String> rows =
        jdbcTemplate.queryForList(
            """
            select distinct sc.code
            from aircraft_layout al
            join aircraft_layout_zones z on z.aircraft_layout_id = al.id
            join seat_classes sc on sc.id = z.seat_class_id
            where al.aircraft_id = ?
            """,
            String.class,
            flight.getAircraft().getId());
    return rows.stream()
        .filter(StringUtils::hasText)
        .map(String::toUpperCase)
        .collect(java.util.stream.Collectors.toSet());
  }

  private String writeTaxBreakdown(List<RouteBasedFarePricingService.TaxLineItem> items) {
    List<Map<String, Object>> breakdown = new ArrayList<>();
    for (RouteBasedFarePricingService.TaxLineItem item : items) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", item.code());
      row.put("name", item.name());
      row.put("amount", item.amount());
      breakdown.add(row);
    }
    String breakdownJson = "[]";
    try {
      breakdownJson = objectMapper.writeValueAsString(breakdown);
    } catch (JsonProcessingException exception) {
      LOG.warn("Unable to serialize tax breakdown JSON: {}", exception.getMessage());
    }
    return breakdownJson;
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase();
  }
}
