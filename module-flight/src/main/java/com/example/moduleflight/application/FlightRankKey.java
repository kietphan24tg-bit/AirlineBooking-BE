package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;

public record FlightRankKey(
    FlightSortBy sortBy,
    BigDecimal primaryMetric,
    BigDecimal secondaryMetric,
    BigDecimal tertiaryMetric,
    OffsetDateTime departureTime,
    long flightId,
    long fareId)
    implements Comparable<FlightRankKey> {

  private static final Comparator<BigDecimal> DECIMAL_ORDER =
      Comparator.nullsLast(Comparator.naturalOrder());
  private static final Comparator<OffsetDateTime> DEPARTURE_ORDER =
      Comparator.nullsLast(Comparator.naturalOrder());

  @Override
  public int compareTo(FlightRankKey other) {
    int bySort = this.sortBy.compareTo(other.sortBy);
    if (bySort != 0) {
      return bySort;
    }
    int byPrimary = DECIMAL_ORDER.compare(this.primaryMetric, other.primaryMetric);
    if (byPrimary != 0) {
      return byPrimary;
    }
    int bySecondary = DECIMAL_ORDER.compare(this.secondaryMetric, other.secondaryMetric);
    if (bySecondary != 0) {
      return bySecondary;
    }
    int byTertiary = DECIMAL_ORDER.compare(this.tertiaryMetric, other.tertiaryMetric);
    if (byTertiary != 0) {
      return byTertiary;
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
