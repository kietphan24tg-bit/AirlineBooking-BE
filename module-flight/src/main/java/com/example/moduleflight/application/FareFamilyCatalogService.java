package com.example.moduleflight.application;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FareClassPriceEntity;
import com.example.moduleflight.repository.FareClassPriceRepository;
import com.example.moduleflight.repository.FareClassRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FareFamilyCatalogService {

  private final FareClassRepository fareClassRepository;
  private final FareClassPriceRepository fareClassPriceRepository;

  public FareFamilyCatalogService(
      FareClassRepository fareClassRepository, FareClassPriceRepository fareClassPriceRepository) {
    this.fareClassRepository = fareClassRepository;
    this.fareClassPriceRepository = fareClassPriceRepository;
  }

  @Transactional(readOnly = true)
  public List<FareFamilyView> listFareFamilies(String countryCode, String currency) {
    String resolvedCountryCode = normalizeCountryCode(countryCode);
    String resolvedCurrency = normalizeCurrency(currency);
    return fareClassRepository.findAll().stream()
        .sorted(Comparator.comparing(FareFamilyCatalogService::sortKey))
        .map(
            fareClass ->
                FareFamilyView.fromEntity(
                    fareClass,
                    referencePriceFor(fareClass, resolvedCountryCode, resolvedCurrency)))
        .toList();
  }

  private ReferencePriceView referencePriceFor(
      FareClassEntity fareClass, String countryCode, String currency) {
    return fareClassPriceRepository
        .findByFareClassAndCurrencyAndCountryCode(fareClass, currency, countryCode)
        .map(FareFamilyCatalogService::toReferencePrice)
        .orElse(null);
  }

  private static ReferencePriceView toReferencePrice(FareClassPriceEntity price) {
    return new ReferencePriceView(price.getCountryCode(), price.getCurrency(), price.getBasePrice());
  }

  private static int sortKey(FareClassEntity fareClass) {
    String cabinClass =
        fareClass.getCabinClass() == null
            ? ""
            : fareClass.getCabinClass().trim().toUpperCase(Locale.ROOT);
    return switch (cabinClass) {
      case "ECONOMY" -> 1;
      case "PREMIUM_ECONOMY" -> 2;
      case "BUSINESS" -> 3;
      case "FIRST" -> 4;
      default -> 99;
    };
  }

  private static String normalizeCountryCode(String countryCode) {
    if (countryCode == null || countryCode.isBlank()) {
      return "GL";
    }
    return countryCode.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeCurrency(String currency) {
    if (currency == null || currency.isBlank()) {
      return "VND";
    }
    return currency.trim().toUpperCase(Locale.ROOT);
  }

  public record FareFamilyView(
      Long fareClassId,
      String fareClassCode,
      String fareClassName,
      String cabinClass,
      Integer baggageCheckinKg,
      Integer baggageHandKg,
      BigDecimal changeFee,
      BigDecimal refundFee,
      boolean refundable,
      boolean changeable,
      boolean priorityCheckin,
      boolean priorityBoarding,
      BigDecimal mileageRate,
      boolean loungeAccess,
      boolean seatSelection,
      String seatSelectionPolicy,
      String mealType,
      boolean wifiIncluded,
      boolean fastTrackSecurity,
      String includedSeatCategoryCode,
      ReferencePriceView referencePrice) {

    public static FareFamilyView fromEntity(
        FareClassEntity fareClass, ReferencePriceView referencePrice) {
      return new FareFamilyView(
          fareClass.getId(),
          fareClass.getCode(),
          fareClass.getName(),
          fareClass.getCabinClass(),
          fareClass.getBaggageCheckinKg(),
          fareClass.getBaggageHandKg(),
          fareClass.getChangeFee(),
          fareClass.getRefundFee(),
          fareClass.isRefundable(),
          fareClass.isChangeable(),
          fareClass.isPriorityCheckin(),
          fareClass.isPriorityBoarding(),
          fareClass.getMileageRate(),
          fareClass.isLoungeAccess(),
          fareClass.isSeatSelection(),
          fareClass.getSeatSelectionPolicy(),
          fareClass.getMealType(),
          fareClass.isWifiIncluded(),
          fareClass.isFastTrackSecurity(),
          fareClass.getIncludedSeatCategory() != null ? fareClass.getIncludedSeatCategory().getCode() : null,
          referencePrice);
    }
  }

  public record ReferencePriceView(String countryCode, String currency, BigDecimal amount) {}
}
