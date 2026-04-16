package com.example.moduleflight.application;

import com.example.moduleflight.application.PromotionBatchPreviewService.PromoPreview;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FlightRankingService {

  public FlightRankKey buildFareRankKey(
      FlightFareEntity fare, PromoPreview preview, BigDecimal originalTotalPrice) {
    Objects.requireNonNull(fare, "fare must not be null");
    Objects.requireNonNull(fare.getFlight(), "fare.getFlight() must not be null");

    int promoRank;
    if (preview == null) {
      promoRank = 2;
    } else {
      String status = preview.status();
      promoRank =
          switch (status) {
            case "APPLIED" -> 0;
            case "MAY_APPLY" -> 1;
            case null, default -> 2;
          };
    }

    BigDecimal baseOriginal = Objects.requireNonNullElse(originalTotalPrice, BigDecimal.ZERO);
    BigDecimal effectivePrice =
        preview != null && preview.discountedPrice() != null
            ? preview.discountedPrice()
            : baseOriginal;
    effectivePrice = Objects.requireNonNullElse(effectivePrice, BigDecimal.ZERO);

    OffsetDateTime departureTime = fare.getFlight().getDepartureTime();
    return new FlightRankKey(
        promoRank,
        effectivePrice,
        departureTime,
        fare.getFlight().getId() == null ? 0L : fare.getFlight().getId(),
        fare.getId() == null ? 0L : fare.getId());
  }
}
