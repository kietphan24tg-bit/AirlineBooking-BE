package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;

public record FlightRankKey(
    int promoRank,
    BigDecimal effectivePrice,
    OffsetDateTime departureTime,
    long flightId,
    long fareId)
    implements Comparable<FlightRankKey> {

  private static final Comparator<BigDecimal> PRICE_ORDER =
      Comparator.nullsLast(Comparator.naturalOrder());
  private static final Comparator<OffsetDateTime> DEPARTURE_ORDER =
      Comparator.nullsLast(Comparator.naturalOrder());

  @Override
  public int compareTo(FlightRankKey other) {
    int byPromoRank = Integer.compare(this.promoRank, other.promoRank);
    if (byPromoRank != 0) {
      return byPromoRank;
    }
    int byPrice = PRICE_ORDER.compare(this.effectivePrice, other.effectivePrice);
    if (byPrice != 0) {
      return byPrice;
    }
    int byDeparture = DEPARTURE_ORDER.compare(this.departureTime, other.departureTime);
    if (byDeparture != 0) {
      return byDeparture;
    }
    int byFlightId = Long.compare(this.flightId, other.flightId);
    if (byFlightId != 0) {
      return byFlightId;
    }
    return Long.compare(this.fareId, other.fareId);
  }
}
