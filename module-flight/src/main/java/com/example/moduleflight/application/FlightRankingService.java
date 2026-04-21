package com.example.moduleflight.application;

import com.example.moduleflight.application.PromotionBatchPreviewService.PromoPreview;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FlightRankingService {

  public FlightRankKey buildFareRankKey(
      FlightSortBy sortBy,
      FlightFareEntity fare,
      PromoPreview preview,
      BigDecimal originalTotalPrice) {
    Objects.requireNonNull(fare, "fare must not be null");
    Objects.requireNonNull(fare.getFlight(), "fare.getFlight() must not be null");

    BigDecimal baseOriginal = Objects.requireNonNullElse(originalTotalPrice, BigDecimal.ZERO);
    BigDecimal effectivePrice = effectivePrice(preview, baseOriginal);
    OffsetDateTime departureTime = fare.getFlight().getDepartureTime();
    long durationMinutes = durationMinutes(fare);
    long experienceScore = experienceScore(fare.getFareClass());
    return switch (sortBy) {
      case RECOMMENDED ->
          new FlightRankKey(
              sortBy,
              BigDecimal.valueOf(promoRank(preview)),
              effectivePrice,
              null,
              departureTime,
              safeFlightId(fare),
              safeFareId(fare));
      case CHEAPEST ->
          new FlightRankKey(
              sortBy,
              effectivePrice,
              null,
              null,
              departureTime,
              safeFlightId(fare),
              safeFareId(fare));
      case FASTEST ->
          new FlightRankKey(
              sortBy,
              BigDecimal.valueOf(durationMinutes),
              effectivePrice,
              null,
              departureTime,
              safeFlightId(fare),
              safeFareId(fare));
      case BEST_EXPERIENCE ->
          new FlightRankKey(
              sortBy,
              BigDecimal.valueOf(-experienceScore),
              BigDecimal.valueOf(durationMinutes),
              effectivePrice,
              departureTime,
              safeFlightId(fare),
              safeFareId(fare));
    };
  }

  private BigDecimal effectivePrice(PromoPreview preview, BigDecimal originalTotalPrice) {
    BigDecimal effectivePrice =
        preview != null && preview.discountedPrice() != null
            ? preview.discountedPrice()
            : originalTotalPrice;
    return Objects.requireNonNullElse(effectivePrice, BigDecimal.ZERO);
  }

  private int promoRank(PromoPreview preview) {
    if (preview == null) {
      return 2;
    }
    String status = preview.status();
    return switch (status) {
      case "APPLIED" -> 0;
      case "MAY_APPLY" -> 1;
      case null, default -> 2;
    };
  }

  private long durationMinutes(FlightFareEntity fare) {
    OffsetDateTime departure = fare.getFlight().getDepartureTime();
    OffsetDateTime arrival = fare.getFlight().getArrivalTime();
    if (departure == null || arrival == null) {
      return Long.MAX_VALUE;
    }
    return Math.max(0L, ChronoUnit.MINUTES.between(departure, arrival));
  }

  private long experienceScore(FareClassEntity fareClass) {
    if (fareClass == null) {
      return Long.MIN_VALUE;
    }
    long score = cabinClassWeight(fareClass.getCabinClass());
    score += boolWeight(fareClass.isLoungeAccess(), 20_000);
    score += boolWeight(fareClass.isFastTrackSecurity(), 15_000);
    score += boolWeight(fareClass.isPriorityBoarding(), 12_000);
    score += boolWeight(fareClass.isPriorityCheckin(), 10_000);
    score += boolWeight(fareClass.isSeatSelection(), 8_000);
    score += boolWeight(fareClass.isWifiIncluded(), 7_000);
    score += boolWeight(fareClass.isRefundable(), 4_000);
    score += boolWeight(fareClass.isChangeable(), 3_000);
    score += Math.max(0, Objects.requireNonNullElse(fareClass.getBaggageCheckinKg(), 0)) * 10L;
    score += Math.max(0, Objects.requireNonNullElse(fareClass.getBaggageHandKg(), 0)) * 8L;
    BigDecimal mileageRate =
        Objects.requireNonNullElse(fareClass.getMileageRate(), BigDecimal.ZERO);
    score += mileageRate.multiply(BigDecimal.valueOf(100)).longValue();
    return score;
  }

  private long cabinClassWeight(String cabinClass) {
    if (!StringUtils.hasText(cabinClass)) {
      return 0L;
    }
    return switch (cabinClass.trim().toUpperCase()) {
      case "FIRST" -> 300_000L;
      case "BUSINESS" -> 200_000L;
      case "PREMIUM_ECONOMY" -> 100_000L;
      default -> 0L;
    };
  }

  private long boolWeight(boolean enabled, long weight) {
    return enabled ? weight : 0L;
  }

  private long safeFlightId(FlightFareEntity fare) {
    return fare.getFlight().getId() == null ? 0L : fare.getFlight().getId();
  }

  private long safeFareId(FlightFareEntity fare) {
    return fare.getId() == null ? 0L : fare.getId();
  }
}
