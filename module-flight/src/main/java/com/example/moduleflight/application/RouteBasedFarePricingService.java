package com.example.moduleflight.application;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FareClassPriceEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.MarketBasePriceEntity;
import com.example.moduleflight.entity.PricingMarketProfileEntity;
import com.example.moduleflight.entity.RouteBasePriceEntity;
import com.example.moduleflight.entity.TaxFeeEntity;
import com.example.moduleflight.repository.FareClassMultiplierRepository;
import com.example.moduleflight.repository.FareClassPriceRepository;
import com.example.moduleflight.repository.MarketBasePriceRepository;
import com.example.moduleflight.repository.PricingMarketProfileRepository;
import com.example.moduleflight.repository.RouteBasePriceRepository;
import com.example.moduleflight.repository.TaxFeeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RouteBasedFarePricingService {

  private static final String DEFAULT_MARKET_COUNTRY = "GL";
  private static final String DEFAULT_TAX_PROFILE = "GLOBAL_STANDARD";
  private static final String LEGACY_TAX_PROFILE = "LEGACY";

  private final RouteBasePriceRepository routeBasePriceRepository;
  private final MarketBasePriceRepository marketBasePriceRepository;
  private final FareClassMultiplierRepository fareClassMultiplierRepository;
  private final PricingMarketProfileRepository pricingMarketProfileRepository;
  private final FareClassPriceRepository fareClassPriceRepository;
  private final TaxFeeRepository taxFeeRepository;
  private final CountryCurrencyResolver countryCurrencyResolver;

  public RouteBasedFarePricingService(
      RouteBasePriceRepository routeBasePriceRepository,
      MarketBasePriceRepository marketBasePriceRepository,
      FareClassMultiplierRepository fareClassMultiplierRepository,
      PricingMarketProfileRepository pricingMarketProfileRepository,
      FareClassPriceRepository fareClassPriceRepository,
      TaxFeeRepository taxFeeRepository,
      CountryCurrencyResolver countryCurrencyResolver) {
    this.routeBasePriceRepository = routeBasePriceRepository;
    this.marketBasePriceRepository = marketBasePriceRepository;
    this.fareClassMultiplierRepository = fareClassMultiplierRepository;
    this.pricingMarketProfileRepository = pricingMarketProfileRepository;
    this.fareClassPriceRepository = fareClassPriceRepository;
    this.taxFeeRepository = taxFeeRepository;
    this.countryCurrencyResolver = countryCurrencyResolver;
  }

  public Optional<PricingResult> priceFare(FlightEntity flight, FareClassEntity fareClass) {
    if (flight == null || fareClass == null) {
      return Optional.empty();
    }

    String fareCode = normalize(fareClass.getCode());
    PricingTripScope tripScope = countryCurrencyResolver.resolveTripScope(flight);
    String originCountryCode = countryCurrencyResolver.resolveCountryCode(flight);
    String destinationCountryCode = countryCurrencyResolver.resolveDestinationCountryCode(flight);
    PricingMarketProfileEntity profile = resolveMarketProfile(originCountryCode);
    String currency = normalize(profile.getCurrency());

    if ("FIRST".equals(fareCode) && !profile.isAllowFirst()) {
      return Optional.empty();
    }

    BaseFareResolution baseResolution =
        resolveBaseFare(
            flight,
            fareClass,
            fareCode,
            tripScope,
            originCountryCode,
            destinationCountryCode,
            currency);
    List<TaxFeeEntity> taxes = resolveTaxes(profile.getTaxProfileCode());
    List<TaxLineItem> taxBreakdown = buildTaxBreakdown(baseResolution.baseFare(), taxes, currency);
    BigDecimal totalTaxAmount =
        taxBreakdown.stream()
            .map(TaxLineItem::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalPrice = baseResolution.baseFare().add(totalTaxAmount).setScale(2, RoundingMode.HALF_UP);

    return Optional.of(
        new PricingResult(
            currency,
            tripScope,
            baseResolution.sourceLevel(),
            profile.getTaxProfileCode(),
            baseResolution.baseFare(),
            totalTaxAmount,
            totalPrice,
            taxBreakdown));
  }

  private BaseFareResolution resolveBaseFare(
      FlightEntity flight,
      FareClassEntity fareClass,
      String fareCode,
      PricingTripScope tripScope,
      String originCountryCode,
      String destinationCountryCode,
      String currency) {
    BigDecimal multiplier = resolveMultiplier(fareCode, tripScope);
    LocalDate flightDate = flight.getFlightDate();
    String originAirportCode = normalize(flight.getDepartureAirport() == null ? null : flight.getDepartureAirport().getCode());
    String destinationAirportCode = normalize(flight.getArrivalAirport() == null ? null : flight.getArrivalAirport().getCode());

    Optional<RouteBasePriceEntity> routePrice =
        routeBasePriceRepository.findActiveByRouteAndCurrency(
            originAirportCode, destinationAirportCode, currency, flightDate);
    if (routePrice.isPresent()) {
      return new BaseFareResolution(
          multiply(routePrice.get().getBaseEconomyPrice(), multiplier), PricingSourceLevel.ROUTE);
    }

    Optional<MarketBasePriceEntity> marketPrice =
        marketBasePriceRepository.findByOriginCountryCodeAndDestinationCountryCodeAndCurrency(
            originCountryCode, destinationCountryCode, currency);
    if (marketPrice.isPresent()) {
      return new BaseFareResolution(
          multiply(marketPrice.get().getBaseEconomyPrice(), multiplier), PricingSourceLevel.MARKET);
    }

    Optional<MarketBasePriceEntity> globalMarketPrice =
        marketBasePriceRepository.findByOriginCountryCodeAndDestinationCountryCodeAndCurrency(
            DEFAULT_MARKET_COUNTRY, DEFAULT_MARKET_COUNTRY, currency);
    if (globalMarketPrice.isPresent()) {
      return new BaseFareResolution(
          multiply(globalMarketPrice.get().getBaseEconomyPrice(), multiplier),
          PricingSourceLevel.GLOBAL_MARKET);
    }

    BigDecimal legacyBasePrice = resolveLegacyBasePrice(fareClass, currency, originCountryCode);
    return new BaseFareResolution(legacyBasePrice, PricingSourceLevel.LEGACY);
  }

  private BigDecimal resolveLegacyBasePrice(
      FareClassEntity fareClass, String currency, String originCountryCode) {
    Optional<FareClassPriceEntity> direct =
        fareClassPriceRepository.findByFareClassAndCurrencyAndCountryCode(
            fareClass, currency, originCountryCode);
    if (direct.isPresent()) {
      return scale(direct.get().getBasePrice());
    }

    Optional<FareClassPriceEntity> global =
        fareClassPriceRepository.findByFareClassAndCurrencyAndCountryCode(
            fareClass, currency, DEFAULT_MARKET_COUNTRY);
    if (global.isPresent()) {
      return scale(global.get().getBasePrice());
    }

    Optional<FareClassPriceEntity> defaultGlobal =
        fareClassPriceRepository.findByFareClassAndCurrencyAndCountryCode(
            fareClass, countryCurrencyResolver.resolveCurrency(DEFAULT_MARKET_COUNTRY), DEFAULT_MARKET_COUNTRY);
    if (defaultGlobal.isPresent()) {
      return scale(defaultGlobal.get().getBasePrice());
    }

    String normalizedCode = normalize(fareClass.getCode());
    return switch (normalizedCode) {
      case "ECONOMY" -> new BigDecimal("1200000.00");
      case "PREMIUM" -> new BigDecimal("2900000.00");
      case "BUSINESS" -> new BigDecimal("7300000.00");
      case "FIRST" -> new BigDecimal("12300000.00");
      default -> new BigDecimal("1200000.00");
    };
  }

  private PricingMarketProfileEntity resolveMarketProfile(String originCountryCode) {
    return pricingMarketProfileRepository
        .findByCountryCode(normalize(originCountryCode))
        .or(() -> pricingMarketProfileRepository.findByCountryCode(DEFAULT_MARKET_COUNTRY))
        .orElseGet(
            () -> {
              PricingMarketProfileEntity fallback = new PricingMarketProfileEntity();
              fallback.setCountryCode(DEFAULT_MARKET_COUNTRY);
              fallback.setCurrency(countryCurrencyResolver.resolveCurrency(DEFAULT_MARKET_COUNTRY));
              fallback.setAllowFirst(false);
              fallback.setTaxProfileCode(DEFAULT_TAX_PROFILE);
              return fallback;
            });
  }

  private BigDecimal resolveMultiplier(String fareCode, PricingTripScope tripScope) {
    return fareClassMultiplierRepository
        .findByFareCodeAndTripScope(normalize(fareCode), tripScope.name())
        .map(com.example.moduleflight.entity.FareClassMultiplierEntity::getMultiplier)
        .map(this::scale)
        .orElse(
            switch (normalize(fareCode)) {
              case "PREMIUM" -> new BigDecimal("1.45");
              case "BUSINESS" -> new BigDecimal("2.20");
              case "FIRST" -> new BigDecimal("3.20");
              default -> BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
            });
  }

  private List<TaxFeeEntity> resolveTaxes(String taxProfileCode) {
    String normalizedProfile = normalize(taxProfileCode);
    List<TaxFeeEntity> exact =
        taxFeeRepository.findByIsActiveTrueAndApplyToAndTaxProfileCode("ADULT", normalizedProfile);
    if (!exact.isEmpty()) {
      return exact;
    }
    if (!DEFAULT_TAX_PROFILE.equals(normalizedProfile)) {
      List<TaxFeeEntity> global =
          taxFeeRepository.findByIsActiveTrueAndApplyToAndTaxProfileCode("ADULT", DEFAULT_TAX_PROFILE);
      if (!global.isEmpty()) {
        return global;
      }
    }
    return taxFeeRepository.findByIsActiveTrueAndApplyToAndTaxProfileCode("ADULT", LEGACY_TAX_PROFILE);
  }

  private List<TaxLineItem> buildTaxBreakdown(
      BigDecimal baseFare, List<TaxFeeEntity> taxes, String currency) {
    List<TaxLineItem> breakdown = new ArrayList<>();
    for (TaxFeeEntity tax : taxes) {
      BigDecimal amount = BigDecimal.ZERO;
      if ("PERCENT".equalsIgnoreCase(tax.getAmountType())) {
        amount = baseFare.multiply(tax.getValue()).setScale(2, RoundingMode.HALF_UP);
      } else if ("FIXED".equalsIgnoreCase(tax.getAmountType())) {
        amount = scale(tax.getValue());
      }
      breakdown.add(new TaxLineItem(tax.getCode(), tax.getName(), amount, currency));
    }
    return breakdown;
  }

  private BigDecimal multiply(BigDecimal amount, BigDecimal multiplier) {
    return scale(amount.multiply(multiplier));
  }

  private BigDecimal scale(BigDecimal value) {
    return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public enum PricingSourceLevel {
    ROUTE,
    MARKET,
    GLOBAL_MARKET,
    LEGACY
  }

  public record PricingResult(
      String currency,
      PricingTripScope tripScope,
      PricingSourceLevel sourceLevel,
      String taxProfileCode,
      BigDecimal baseFare,
      BigDecimal totalTaxAmount,
      BigDecimal totalPrice,
      List<TaxLineItem> taxBreakdown) {}

  public record TaxLineItem(String code, String name, BigDecimal amount, String currency) {}

  private record BaseFareResolution(BigDecimal baseFare, PricingSourceLevel sourceLevel) {}
}
