package com.example.moduleflight.application;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.entity.TaxFeeEntity;
import com.example.moduleflight.repository.FareClassRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleflight.repository.TaxFeeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightFareGenerationService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightFareGenerationService.class);

  private final FareClassRepository fareClassRepository;
  private final TaxFeeRepository taxFeeRepository;
  private final FlightFareRepository flightFareRepository;
  private final ObjectMapper objectMapper;

  public FlightFareGenerationService(
      FareClassRepository fareClassRepository,
      TaxFeeRepository taxFeeRepository,
      FlightFareRepository flightFareRepository,
      ObjectMapper objectMapper) {
    this.fareClassRepository = fareClassRepository;
    this.taxFeeRepository = taxFeeRepository;
    this.flightFareRepository = flightFareRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void generateFaresForFlight(FlightEntity flight) {
    if (flight == null || flight.getId() == null) {
      return;
    }

    List<FareClassEntity> fares = fareClassRepository.findAll();
    if (fares.isEmpty()) {
      LOG.warn("Skip flight fare generation: no fare classes configured.");
      return;
    }
    List<TaxFeeEntity> taxes = taxFeeRepository.findByIsActiveTrueAndApplyTo("ADULT");
    OffsetDateTime now = OffsetDateTime.now();
    List<FlightFareEntity> toSave = new ArrayList<>();

    for (FareClassEntity fareClass : fares) {
      BigDecimal basePrice = resolveBasePrice(fareClass);
      TaxCalculation taxCalculation = calculateTax(basePrice, taxes);
      BigDecimal totalPrice = basePrice.add(taxCalculation.totalTaxAmount());

      FlightFareEntity entity =
          flightFareRepository
              .findByFlightAndFareClass(flight, fareClass)
              .orElseGet(FlightFareEntity::new);
      entity.setFlight(flight);
      entity.setFareClass(fareClass);
      entity.setBasePrice(basePrice);
      entity.setTaxBreakdown(taxCalculation.breakdownJson());
      entity.setTotalTaxAmount(taxCalculation.totalTaxAmount());
      entity.setTotalPrice(totalPrice);
      if (entity.getId() == null || entity.getStatus() == null) {
        entity.setStatus("AVAILABLE");
      }
      entity.setUpdatedAt(now);
      toSave.add(entity);
    }

    flightFareRepository.saveAll(toSave);
  }

  private BigDecimal resolveBasePrice(FareClassEntity fareClass) {
    String code = fareClass.getCode() == null ? "" : fareClass.getCode().trim().toUpperCase();
    return switch (code) {
      case "ECONOMY_SAVER" -> new BigDecimal("1000000");
      case "ECONOMY_FLEX" -> new BigDecimal("1300000");
      case "BUSINESS" -> new BigDecimal("3000000");
      default -> resolveBasePriceByCabinClass(fareClass.getCabinClass());
    };
  }

  private BigDecimal resolveBasePriceByCabinClass(String cabinClass) {
    if (!StringUtils.hasText(cabinClass)) {
      return new BigDecimal("1000000");
    }
    return switch (cabinClass.trim().toUpperCase()) {
      case "ECONOMY" -> new BigDecimal("1100000");
      case "PREMIUM_ECONOMY" -> new BigDecimal("1800000");
      case "BUSINESS", "FIRST" -> new BigDecimal("3000000");
      default -> new BigDecimal("1000000");
    };
  }

  private TaxCalculation calculateTax(BigDecimal basePrice, List<TaxFeeEntity> taxes) {
    List<Map<String, Object>> breakdown = new ArrayList<>();
    BigDecimal totalTaxAmount = BigDecimal.ZERO;

    for (TaxFeeEntity tax : taxes) {
      BigDecimal amount = BigDecimal.ZERO;
      if ("PERCENT".equalsIgnoreCase(tax.getAmountType())) {
        amount = basePrice.multiply(tax.getValue()).setScale(2, RoundingMode.HALF_UP);
      } else if ("FIXED".equalsIgnoreCase(tax.getAmountType())) {
        amount = tax.getValue().setScale(2, RoundingMode.HALF_UP);
      }

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("code", tax.getCode());
      item.put("name", tax.getName());
      item.put("amount", amount);
      breakdown.add(item);
      totalTaxAmount = totalTaxAmount.add(amount);
    }

    String breakdownJson = "[]";
    try {
      breakdownJson = objectMapper.writeValueAsString(breakdown);
    } catch (JsonProcessingException exception) {
      LOG.warn("Unable to serialize tax breakdown JSON: {}", exception.getMessage());
    }
    return new TaxCalculation(totalTaxAmount.setScale(2, RoundingMode.HALF_UP), breakdownJson);
  }

  private record TaxCalculation(BigDecimal totalTaxAmount, String breakdownJson) {}
}
