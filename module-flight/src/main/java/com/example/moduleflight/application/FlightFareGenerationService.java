package com.example.moduleflight.application;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FareClassPriceEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.entity.TaxFeeEntity;
import com.example.moduleflight.repository.FareClassPriceRepository;
import com.example.moduleflight.repository.FareClassRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleflight.repository.TaxFeeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
  private static final String GLOBAL_COUNTRY_CODE = "GL";
  private static final String DEFAULT_CURRENCY = "VND";
  private static final List<String> SUPPORTED_FARE_CODES =
      List.of("ECONOMY", "PREMIUM", "BUSINESS", "FIRST");

  private final FareClassRepository fareClassRepository;
  private final FareClassPriceRepository fareClassPriceRepository;
  private final TaxFeeRepository taxFeeRepository;
  private final FlightFareRepository flightFareRepository;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbcTemplate;
  private final FlightInventoryMetadataService flightInventoryMetadataService;

  public FlightFareGenerationService(
      FareClassRepository fareClassRepository,
      FareClassPriceRepository fareClassPriceRepository,
      TaxFeeRepository taxFeeRepository,
      FlightFareRepository flightFareRepository,
      ObjectMapper objectMapper,
      JdbcTemplate jdbcTemplate,
      FlightInventoryMetadataService flightInventoryMetadataService) {
    this.fareClassRepository = fareClassRepository;
    this.fareClassPriceRepository = fareClassPriceRepository;
    this.taxFeeRepository = taxFeeRepository;
    this.flightFareRepository = flightFareRepository;
    this.objectMapper = objectMapper;
    this.jdbcTemplate = jdbcTemplate;
    this.flightInventoryMetadataService = flightInventoryMetadataService;
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
    String countryCode = resolveCountryCode(flight);
    String currency = resolveCurrency(countryCode);
    List<TaxFeeEntity> taxes = taxFeeRepository.findByIsActiveTrueAndApplyTo("ADULT");
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
      BigDecimal basePrice = resolveBasePrice(fareClass, currency, countryCode);
      TaxCalculation taxCalculation = calculateTax(basePrice, taxes);
      BigDecimal totalPrice = basePrice.add(taxCalculation.totalTaxAmount());

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

  private BigDecimal resolveBasePrice(
      FareClassEntity fareClass, String currency, String countryCode) {
    if (StringUtils.hasText(countryCode)) {
      BigDecimal countrySpecific = resolveBasePriceFromConfig(fareClass, currency, countryCode);
      if (countrySpecific != null) {
        return countrySpecific;
      }
    }

    BigDecimal currencyGlobal =
        resolveBasePriceFromConfig(fareClass, currency, GLOBAL_COUNTRY_CODE);
    if (currencyGlobal != null) {
      return currencyGlobal;
    }

    BigDecimal defaultGlobal =
        resolveBasePriceFromConfig(fareClass, DEFAULT_CURRENCY, GLOBAL_COUNTRY_CODE);
    if (defaultGlobal != null) {
      return defaultGlobal;
    }

    String code = normalize(fareClass.getCode());
    return switch (code) {
      case "ECONOMY" -> new BigDecimal("1200000");
      case "PREMIUM" -> new BigDecimal("2900000");
      case "BUSINESS" -> new BigDecimal("7300000");
      case "FIRST" -> new BigDecimal("12300000");
      default -> resolveBasePriceByCabinClass(fareClass.getCabinClass());
    };
  }

  private BigDecimal resolveBasePriceFromConfig(
      FareClassEntity fareClass, String currency, String countryCode) {
    return fareClassPriceRepository
        .findByFareClassAndCurrencyAndCountryCode(
            fareClass, normalize(currency), normalize(countryCode))
        .map(FareClassPriceEntity::getBasePrice)
        .orElse(null);
  }

  private String resolveCountryCode(FlightEntity flight) {
    if (flight.getDepartureAirport() == null
        || !StringUtils.hasText(flight.getDepartureAirport().getCountry())) {
      return null;
    }
    String normalizedCountry = normalize(flight.getDepartureAirport().getCountry());
    return switch (normalizedCountry) {
      case "VIETNAM", "VIET_NAM" -> "VN";
      case "UNITED_STATES", "UNITED_STATES_OF_AMERICA", "USA", "US" -> "US";
      case "UNITED_KINGDOM", "UK", "GREAT_BRITAIN", "BRITAIN" -> "GB";
      case "GERMANY", "DEUTSCHLAND" -> "DE";
      case "JAPAN" -> "JP";
      case "AUSTRALIA" -> "AU";
      case "SINGAPORE" -> "SG";
      default -> null;
    };
  }

  private String resolveCurrency(String countryCode) {
    if (!StringUtils.hasText(countryCode)) {
      return DEFAULT_CURRENCY;
    }
    return switch (normalize(countryCode)) {
      case "VN" -> "VND";
      case "US" -> "USD";
      case "GB" -> "GBP";
      case "DE" -> "EUR";
      case "JP" -> "JPY";
      case "AU" -> "AUD";
      case "SG" -> "SGD";
      default -> DEFAULT_CURRENCY;
    };
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase().replace(' ', '_');
  }

  private BigDecimal resolveBasePriceByCabinClass(String cabinClass) {
    if (!StringUtils.hasText(cabinClass)) {
      return new BigDecimal("1200000");
    }
    return switch (normalize(cabinClass)) {
      case "ECONOMY" -> new BigDecimal("1200000");
      case "PREMIUM_ECONOMY" -> new BigDecimal("2900000");
      case "BUSINESS" -> new BigDecimal("7300000");
      case "FIRST" -> new BigDecimal("12300000");
      default -> new BigDecimal("1200000");
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
