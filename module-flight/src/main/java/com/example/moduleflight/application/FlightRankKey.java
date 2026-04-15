package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FlightRankKey(
    int promoRank, BigDecimal effectivePrice, OffsetDateTime departureTime, long flightId, long fareId)
    implements Comparable<FlightRankKey> {

  @Override
  public int compareTo(FlightRankKey other) {
    int byPromoRank = Integer.compare(this.promoRank, other.promoRank);
    if (byPromoRank != 0) {
      return byPromoRank;
    }
    int byPrice = this.effectivePrice.compareTo(other.effectivePrice);
    if (byPrice != 0) {
      return byPrice;
    }
    int byDeparture = this.departureTime.compareTo(other.departureTime);
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
