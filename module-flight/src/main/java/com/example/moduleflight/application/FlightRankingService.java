package com.example.moduleflight.application;

import com.example.moduleflight.application.PromotionBatchPreviewService.PromoPreview;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class FlightRankingService {

  public FlightRankKey buildFareRankKey(
      FlightFareEntity fare, PromoPreview preview, BigDecimal originalTotalPrice) {
    int promoRank =
        switch (preview.status()) {
          case "APPLIED" -> 0;
          case "MAY_APPLY" -> 1;
          default -> 2;
        };
    BigDecimal effectivePrice =
        preview.discountedPrice() != null ? preview.discountedPrice() : originalTotalPrice;
    OffsetDateTime departureTime = fare.getFlight().getDepartureTime();
    return new FlightRankKey(
        promoRank,
        effectivePrice,
        departureTime,
        fare.getFlight().getId() == null ? 0L : fare.getFlight().getId(),
        fare.getId() == null ? 0L : fare.getId());
  }
}
