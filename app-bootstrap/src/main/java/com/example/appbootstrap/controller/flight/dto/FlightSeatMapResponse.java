package com.example.appbootstrap.controller.flight.dto;

import com.example.appbootstrap.application.flight.FlightSeatMapService;
import java.math.BigDecimal;
import java.util.List;

public record FlightSeatMapResponse(
    Long flightId,
    Long fareId,
    Long fareClassId,
    String fareCode,
    String seatSelectionPolicy,
    String includedSeatCategoryCode,
    String currency,
    LayoutResponse layout,
    LegendResponse legend,
    List<RowResponse> rows) {

  public static FlightSeatMapResponse from(FlightSeatMapService.FlightSeatMapView view) {
    return new FlightSeatMapResponse(
        view.flightId(),
        view.fareId(),
        view.fareClassId(),
        view.fareCode(),
        view.seatSelectionPolicy(),
        view.includedSeatCategoryCode(),
        view.currency(),
        LayoutResponse.from(view.layout()),
        LegendResponse.from(view.legend()),
        view.rows().stream().map(RowResponse::from).toList());
  }

  public record LayoutResponse(
      List<String> columns,
      List<String> aisleAfterColumns,
      List<SeatClassZoneResponse> seatClassZones) {
    static LayoutResponse from(FlightSeatMapService.AircraftLayoutView view) {
      return new LayoutResponse(
          view.columns(),
          view.aisleAfterColumns(),
          view.seatClassZones().stream().map(SeatClassZoneResponse::from).toList());
    }
  }

  public record SeatClassZoneResponse(String seatClass, Integer rowStart, Integer rowEnd) {
    static SeatClassZoneResponse from(FlightSeatMapService.SeatClassZoneView view) {
      return new SeatClassZoneResponse(view.seatClass(), view.rowStart(), view.rowEnd());
    }
  }

  public record LegendResponse(
      List<String> statuses, List<String> seatTypes, List<String> seatCategories) {
    static LegendResponse from(FlightSeatMapService.LegendView view) {
      return new LegendResponse(view.statuses(), view.seatTypes(), view.seatCategories());
    }
  }

  public record RowResponse(Integer rowNumber, String seatClass, List<SeatResponse> cells) {
    static RowResponse from(FlightSeatMapService.RowView view) {
      return new RowResponse(
          view.rowNumber(), view.seatClass(), view.cells().stream().map(SeatResponse::from).toList());
    }
  }

  public record SeatResponse(
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
      String reason) {
    static SeatResponse from(FlightSeatMapService.SeatView view) {
      return new SeatResponse(
          view.columnLetter(),
          view.seatId(),
          view.seatNumber(),
          view.seatClass(),
          view.seatType(),
          view.seatCategory(),
          view.status(),
          view.selectable(),
          view.included(),
          view.extraPrice(),
          view.currency(),
          view.reason());
    }
  }
}
