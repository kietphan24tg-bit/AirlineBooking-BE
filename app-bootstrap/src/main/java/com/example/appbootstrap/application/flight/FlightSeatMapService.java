package com.example.appbootstrap.application.flight;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.moduleflight.entity.AircraftLayoutEntity;
import com.example.moduleflight.entity.AircraftLayoutZoneEntity;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.application.SeatSelectionPolicy;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.entity.SeatCategoryEntity;
import com.example.moduleflight.entity.SeatTypeEntity;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AircraftLayoutZoneRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightSeatMapService {

  private static final List<String> LEGEND_STATUSES =
      List.of("AVAILABLE", "RESERVED", "BOOKED", "UNAVAILABLE");
  private static final List<String> LEGEND_SEAT_TYPES = List.of("WINDOW", "MIDDLE", "AISLE");
  private static final List<String> LEGEND_SEAT_CATEGORIES =
      List.of("STANDARD", "PREFERRED", "EXIT_ROW", "EXTRA_LEGROOM");

  private final FlightFareRepository flightFareRepository;
  private final FlightSeatRepository flightSeatRepository;
  private final AircraftLayoutRepository aircraftLayoutRepository;
  private final AircraftLayoutZoneRepository aircraftLayoutZoneRepository;
  private final CountryCurrencyResolver countryCurrencyResolver;

  public FlightSeatMapService(
      FlightFareRepository flightFareRepository,
      FlightSeatRepository flightSeatRepository,
      AircraftLayoutRepository aircraftLayoutRepository,
      AircraftLayoutZoneRepository aircraftLayoutZoneRepository,
      CountryCurrencyResolver countryCurrencyResolver) {
    this.flightFareRepository = flightFareRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.aircraftLayoutRepository = aircraftLayoutRepository;
    this.aircraftLayoutZoneRepository = aircraftLayoutZoneRepository;
    this.countryCurrencyResolver = countryCurrencyResolver;
  }

  @Transactional(readOnly = true)
  public FlightSeatMapView getSeatMap(Long flightId, Long fareId) {
    FlightFareEntity fare =
        flightFareRepository
            .findByIdWithDetails(fareId)
            .orElseThrow(() -> new AppException("FARE_NOT_FOUND", "Fare not found", 404));
    if (fare.getFlight() == null || !flightId.equals(fare.getFlight().getId())) {
      throw new AppException("FARE_NOT_FOUND", "Fare does not belong to the requested flight", 404);
    }

    FareClassEntity fareClass = fare.getFareClass();
    if (fareClass == null || fareClass.getId() == null) {
      throw new AppException("FARE_NOT_FOUND", "Fare class metadata is missing", 404);
    }

    SeatSelectionPolicy selectionPolicy =
        SeatSelectionPolicy.fromValue(fareClass.getSeatSelectionPolicy());
    String currency =
        countryCurrencyResolver.resolveCurrency(countryCurrencyResolver.resolveCountryCode(fare));
    Set<Long> allowedSeatIds =
        flightSeatRepository.findAllowedSeatIdsForFare(flightId, fareClass.getId());
    AircraftLayoutView layout =
        loadAircraftLayout(
            fare.getFlight() == null || fare.getFlight().getAircraft() == null
                ? null
                : fare.getFlight().getAircraft().getId());

    List<SeatView> seats =
        flightSeatRepository.findSeatMapByFlightId(flightId).stream()
            .sorted(
                Comparator.comparing((FlightSeatEntity seat) -> seatRow(seat))
                    .thenComparing(seat -> seatColumn(seat)))
            .map(
                seat ->
                    toSeatView(
                        seat,
                        selectionPolicy,
                        fareClass.getIncludedSeatCategory(),
                        allowedSeatIds,
                        currency))
            .toList();
    List<RowView> rows = toRows(seats, layout.columns());

    return new FlightSeatMapView(
        fare.getFlight().getId(),
        fare.getId(),
        fareClass.getId(),
        fareClass.getCode(),
        selectionPolicy.name(),
        fareClass.getIncludedSeatCategory() == null ? null : fareClass.getIncludedSeatCategory().getCode(),
        currency,
        layout,
        new LegendView(LEGEND_STATUSES, LEGEND_SEAT_TYPES, LEGEND_SEAT_CATEGORIES),
        rows);
  }

  private SeatView toSeatView(
      FlightSeatEntity seat,
      SeatSelectionPolicy selectionPolicy,
      SeatCategoryEntity includedSeatCategory,
      Set<Long> allowedSeatIds,
      String currency) {
    boolean available = "AVAILABLE".equalsIgnoreCase(seat.getStatus());
    boolean allowedForFare = seat.getId() != null && allowedSeatIds.contains(seat.getId());

    String reason = null;
    boolean selectable = false;
    boolean included = false;
    BigDecimal extraPrice = null;

    if (!available) {
      reason = "SEAT_UNAVAILABLE";
    } else if (!allowedForFare) {
      reason = "SEAT_NOT_ALLOWED_FOR_FARE";
    } else if (!selectionPolicy.allowsAdvanceSelection()) {
      reason = selectionPolicy == SeatSelectionPolicy.CHECKIN_ONLY ? "CHECKIN_ONLY" : "NOT_ALLOWED";
    } else {
      selectable = true;
      included = isSeatIncluded(selectionPolicy, seat, includedSeatCategory);
      extraPrice =
          included
              ? BigDecimal.ZERO
              : surchargeAmount(seat.getSeatCategory()).add(surchargeAmount(seat.getSeatType()));
    }

    return new SeatView(
        seatRow(seat),
        seatColumn(seat),
        seat.getId(),
        seat.getSeatNumber(),
        seat.getSeatClass() == null ? null : seat.getSeatClass().getCode(),
        seat.getSeatType() == null ? null : seat.getSeatType().getCode(),
        seat.getSeatCategory() == null ? null : seat.getSeatCategory().getCode(),
        seat.getStatus(),
        selectable,
        included,
        extraPrice,
        selectable ? currency : null,
        reason);
  }

  private AircraftLayoutView loadAircraftLayout(Long aircraftId) {
    if (aircraftId == null) {
      throw new AppException("SEAT_MAP_LAYOUT_NOT_FOUND", "Aircraft layout is missing", 404);
    }
    AircraftLayoutEntity layout =
        aircraftLayoutRepository
            .findByAircraftId(aircraftId)
            .orElseThrow(
                () ->
                    new AppException(
                        "SEAT_MAP_LAYOUT_NOT_FOUND", "Aircraft layout is missing", 404));
    List<String> columns = parseLayoutColumns(layout.getColumns());
    if (columns.isEmpty()) {
      throw new AppException(
          "SEAT_MAP_LAYOUT_NOT_FOUND", "Aircraft layout columns are missing", 404);
    }
    List<SeatClassZoneView> seatClassZones =
        aircraftLayoutZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(layout.getId()).stream()
            .filter(zone -> zone.getSeatClass() != null && StringUtils.hasText(zone.getSeatClass().getCode()))
            .map(this::toSeatClassZoneView)
            .toList();
    return new AircraftLayoutView(columns, resolveAisleAfterColumns(columns), seatClassZones);
  }

  private SeatClassZoneView toSeatClassZoneView(AircraftLayoutZoneEntity zone) {
    return new SeatClassZoneView(
        zone.getSeatClass().getCode(), zone.getRowStart(), zone.getRowEnd());
  }

  private List<RowView> toRows(List<SeatView> seats, List<String> columns) {
    Map<Integer, Map<String, SeatView>> seatsByRow = new TreeMap<>();
    for (SeatView seat : seats) {
      seatsByRow.computeIfAbsent(seat.rowNumber(), ignored -> new TreeMap<>()).put(seat.columnLetter(), seat);
    }
    return seatsByRow.entrySet().stream()
        .map(entry -> toRowView(entry.getKey(), entry.getValue(), columns))
        .toList();
  }

  private RowView toRowView(int rowNumber, Map<String, SeatView> seatByColumn, List<String> columns) {
    List<SeatView> cells =
        columns.stream()
            .map(seatByColumn::get)
            .filter(java.util.Objects::nonNull)
            .toList();
    String seatClass = cells.isEmpty() ? null : cells.getFirst().seatClass();
    return new RowView(rowNumber, seatClass, cells);
  }

  private boolean isSeatIncluded(
      SeatSelectionPolicy selectionPolicy,
      FlightSeatEntity seat,
      SeatCategoryEntity includedSeatCategory) {
    if (!selectionPolicy.isIncluded()
        || includedSeatCategory == null
        || seat.getSeatCategory() == null) {
      return false;
    }
    Integer includedRank = includedSeatCategory.getPricingRank();
    Integer seatRank = seat.getSeatCategory().getPricingRank();
    if (includedRank == null || seatRank == null) {
      return false;
    }
    return seatRank <= includedRank;
  }

  private BigDecimal surchargeAmount(SeatTypeEntity seatType) {
    if (seatType == null || seatType.getSurchargeAmount() == null) {
      return BigDecimal.ZERO;
    }
    return seatType.getSurchargeAmount();
  }

  private BigDecimal surchargeAmount(SeatCategoryEntity seatCategory) {
    if (seatCategory == null || seatCategory.getSurchargeAmount() == null) {
      return BigDecimal.ZERO;
    }
    return seatCategory.getSurchargeAmount();
  }

  private static int seatRow(FlightSeatEntity seat) {
    return seatRow(seat == null ? null : seat.getSeatNumber());
  }

  private static int seatRow(String seatNumber) {
    if (seatNumber == null) {
      return Integer.MAX_VALUE;
    }
    StringBuilder digits = new StringBuilder();
    for (char value : seatNumber.toCharArray()) {
      if (Character.isDigit(value)) {
        digits.append(value);
      } else {
        break;
      }
    }
    if (digits.isEmpty()) {
      return Integer.MAX_VALUE;
    }
    return Integer.parseInt(digits.toString());
  }

  private static String seatColumn(FlightSeatEntity seat) {
    return seatColumn(seat == null ? null : seat.getSeatNumber());
  }

  private static String seatColumn(String seatNumber) {
    if (!StringUtils.hasText(seatNumber)) {
      return "";
    }
    StringBuilder letters = new StringBuilder();
    for (char value : seatNumber.toCharArray()) {
      if (Character.isLetter(value)) {
        letters.append(Character.toUpperCase(value));
      }
    }
    return letters.toString();
  }

  private static List<String> parseLayoutColumns(String rawColumns) {
    if (!StringUtils.hasText(rawColumns)) {
      return List.of();
    }
    String normalized = rawColumns.trim().toUpperCase(Locale.ROOT);
    if (normalized.contains(",")) {
      return java.util.Arrays.stream(normalized.split(","))
          .map(String::trim)
          .filter(StringUtils::hasText)
          .toList();
    }
    return normalized.chars()
        .mapToObj(value -> String.valueOf((char) value))
        .filter(StringUtils::hasText)
        .toList();
  }

  private static List<String> resolveAisleAfterColumns(List<String> columns) {
    int size = columns.size();
    if (size == 6) {
      return List.of(columns.get(2));
    }
    if (size == 8) {
      return List.of(columns.get(2));
    }
    if (size == 9) {
      return List.of(columns.get(2), columns.get(6));
    }
    return List.of();
  }

  public record FlightSeatMapView(
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareCode,
      String seatSelectionPolicy,
      String includedSeatCategoryCode,
      String currency,
      AircraftLayoutView layout,
      LegendView legend,
      List<RowView> rows) {}

  public record AircraftLayoutView(
      List<String> columns, List<String> aisleAfterColumns, List<SeatClassZoneView> seatClassZones) {}

  public record SeatClassZoneView(String seatClass, Integer rowStart, Integer rowEnd) {}

  public record LegendView(
      List<String> statuses, List<String> seatTypes, List<String> seatCategories) {}

  public record RowView(Integer rowNumber, String seatClass, List<SeatView> cells) {}

  public record SeatView(
      Integer rowNumber,
      String columnLetter,
      Long seatId,
      String seatNumber,
      String seatClass,
      String seatType,
      String seatCategory,
      String status,
      boolean selectable,
      boolean included,
      BigDecimal extraPrice,
      String currency,
      String reason) {}
}
