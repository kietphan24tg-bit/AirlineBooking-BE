package com.example.appbootstrap.application.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AircraftLayoutEntity;
import com.example.moduleflight.entity.AircraftLayoutZoneEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.entity.SeatCategoryEntity;
import com.example.moduleflight.entity.SeatClassEntity;
import com.example.moduleflight.entity.SeatTypeEntity;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AircraftLayoutZoneRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightSeatMapServiceTest {

  @Mock private FlightFareRepository flightFareRepository;
  @Mock private FlightSeatRepository flightSeatRepository;
  @Mock private AircraftLayoutRepository aircraftLayoutRepository;
  @Mock private AircraftLayoutZoneRepository aircraftLayoutZoneRepository;
  @Mock private CountryCurrencyResolver countryCurrencyResolver;

  @Test
  void getSeatMap_paidFareReturnsFareAwareSeatPricing() {
    FlightSeatMapService service =
        new FlightSeatMapService(
            flightFareRepository,
            flightSeatRepository,
            aircraftLayoutRepository,
            aircraftLayoutZoneRepository,
            countryCurrencyResolver);
    FlightFareEntity fare = fare(11L, 101L, 5L, "ECONOMY", "PAID", null);
    FlightSeatEntity seat12A =
        seat(501L, "12A", "ECONOMY", "WINDOW", "STANDARD", "AVAILABLE", "20000", "0");
    FlightSeatEntity seat12B =
        seat(502L, "12B", "ECONOMY", "MIDDLE", "STANDARD", "RESERVED", "0", "0");
    FlightSeatEntity seat3C =
        seat(504L, "3C", "ECONOMY", "AISLE", "PREFERRED", "AVAILABLE", "20000", "40000");
    FlightSeatEntity seat1A =
        seat(503L, "1A", "BUSINESS", "WINDOW", "STANDARD", "AVAILABLE", "20000", "0");

    when(flightFareRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(fare));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(aircraftLayoutRepository.findByAircraftId(901L))
        .thenReturn(Optional.of(layout(301L, "ABCDEF")));
    when(aircraftLayoutZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(301L))
        .thenReturn(List.of(zone("BUSINESS", 1, 2), zone("ECONOMY", 3, 30)));
    when(flightSeatRepository.findAllowedSeatIdsForFare(11L, 5L))
        .thenReturn(Set.of(501L, 502L, 504L));
    when(flightSeatRepository.findSeatMapByFlightId(11L))
        .thenReturn(List.of(seat12B, seat1A, seat12A, seat3C));

    FlightSeatMapService.FlightSeatMapView view = service.getSeatMap(11L, 101L);

    assertEquals("PAID", view.seatSelectionPolicy());
    assertEquals(null, view.includedSeatCategoryCode());
    assertEquals(List.of("A", "B", "C", "D", "E", "F"), view.layout().columns());
    assertEquals(List.of("C"), view.layout().aisleAfterColumns());
    assertEquals(3, view.rows().size());
    assertEquals(1, view.rows().getFirst().rowNumber());
    assertEquals("BUSINESS", view.rows().getFirst().seatClass());
    assertEquals("1A", view.rows().getFirst().cells().getFirst().seatNumber());

    FlightSeatMapService.SeatView preferredSeat = view.rows().get(1).cells().getFirst();
    assertEquals("PREFERRED", preferredSeat.seatCategory());
    assertEquals(new BigDecimal("60000"), preferredSeat.extraPrice());

    FlightSeatMapService.SeatView allowedSeat = view.rows().get(2).cells().getFirst();
    assertTrue(allowedSeat.selectable());
    assertFalse(allowedSeat.included());
    assertEquals(new BigDecimal("20000"), allowedSeat.extraPrice());
    assertEquals("VND", allowedSeat.currency());

    FlightSeatMapService.SeatView unavailableSeat = view.rows().get(2).cells().get(1);
    assertFalse(unavailableSeat.selectable());
    assertEquals("SEAT_UNAVAILABLE", unavailableSeat.reason());

    FlightSeatMapService.SeatView cabinMismatchSeat = view.rows().getFirst().cells().getFirst();
    assertFalse(cabinMismatchSeat.selectable());
    assertEquals("SEAT_NOT_ALLOWED_FOR_FARE", cabinMismatchSeat.reason());
  }

  @Test
  void getSeatMap_includedFareWaivesSeatSurcharge() {
    FlightSeatMapService service =
        new FlightSeatMapService(
            flightFareRepository,
            flightSeatRepository,
            aircraftLayoutRepository,
            aircraftLayoutZoneRepository,
            countryCurrencyResolver);
    FlightFareEntity fare =
        fare(11L, 102L, 6L, "BUSINESS", "INCLUDED", includedSeatCategory("EXTRA_LEGROOM", 4));
    FlightSeatEntity seat =
        seat(601L, "2A", "BUSINESS", "WINDOW", "EXTRA_LEGROOM", "AVAILABLE", "20000", "180000");

    when(flightFareRepository.findByIdWithDetails(102L)).thenReturn(Optional.of(fare));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(aircraftLayoutRepository.findByAircraftId(901L))
        .thenReturn(Optional.of(layout(301L, "ABCDEF")));
    when(aircraftLayoutZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(301L))
        .thenReturn(List.of(zone("BUSINESS", 1, 8), zone("ECONOMY", 9, 30)));
    when(flightSeatRepository.findAllowedSeatIdsForFare(11L, 6L)).thenReturn(Set.of(601L));
    when(flightSeatRepository.findSeatMapByFlightId(11L)).thenReturn(List.of(seat));

    FlightSeatMapService.SeatView view =
        service.getSeatMap(11L, 102L).rows().getFirst().cells().getFirst();

    assertTrue(view.selectable());
    assertTrue(view.included());
    assertEquals(BigDecimal.ZERO, view.extraPrice());
  }

  @Test
  void getSeatMap_premiumIncludesStandardButChargesExitRow() {
    FlightSeatMapService service =
        new FlightSeatMapService(
            flightFareRepository,
            flightSeatRepository,
            aircraftLayoutRepository,
            aircraftLayoutZoneRepository,
            countryCurrencyResolver);
    FlightFareEntity fare =
        fare(11L, 103L, 7L, "PREMIUM", "INCLUDED", includedSeatCategory("STANDARD", 1));
    FlightSeatEntity standardSeat =
        seat(701L, "10A", "ECONOMY", "WINDOW", "STANDARD", "AVAILABLE", "20000", "0");
    FlightSeatEntity exitRowSeat =
        seat(702L, "14C", "ECONOMY", "AISLE", "EXIT_ROW", "AVAILABLE", "20000", "120000");

    when(flightFareRepository.findByIdWithDetails(103L)).thenReturn(Optional.of(fare));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(aircraftLayoutRepository.findByAircraftId(901L))
        .thenReturn(Optional.of(layout(301L, "ABCDEF")));
    when(aircraftLayoutZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(301L))
        .thenReturn(List.of(zone("BUSINESS", 1, 8), zone("ECONOMY", 9, 30)));
    when(flightSeatRepository.findAllowedSeatIdsForFare(11L, 7L)).thenReturn(Set.of(701L, 702L));
    when(flightSeatRepository.findSeatMapByFlightId(11L))
        .thenReturn(List.of(exitRowSeat, standardSeat));

    FlightSeatMapService.FlightSeatMapView view = service.getSeatMap(11L, 103L);

    assertEquals("STANDARD", view.includedSeatCategoryCode());
    FlightSeatMapService.SeatView standard = view.rows().getFirst().cells().getFirst();
    assertEquals("STANDARD", standard.seatCategory());
    assertTrue(standard.included());
    assertEquals(BigDecimal.ZERO, standard.extraPrice());

    FlightSeatMapService.SeatView exitRow = view.rows().get(1).cells().getFirst();
    assertEquals("EXIT_ROW", exitRow.seatCategory());
    assertFalse(exitRow.included());
    assertEquals(new BigDecimal("140000"), exitRow.extraPrice());
  }

  @Test
  void getSeatMap_rejectsFareFromDifferentFlight() {
    FlightSeatMapService service =
        new FlightSeatMapService(
            flightFareRepository,
            flightSeatRepository,
            aircraftLayoutRepository,
            aircraftLayoutZoneRepository,
            countryCurrencyResolver);
    FlightFareEntity fare = fare(22L, 101L, 5L, "ECONOMY", "PAID", null);
    when(flightFareRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(fare));

    AppException exception = assertThrows(AppException.class, () -> service.getSeatMap(11L, 101L));

    assertEquals("FARE_NOT_FOUND", exception.getCode());
  }

  private FlightFareEntity fare(
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareCode,
      String selectionPolicy,
      SeatCategoryEntity includedSeatCategory) {
    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    AircraftEntity aircraft = new AircraftEntity();
    aircraft.setId(901L);
    flight.setAircraft(aircraft);

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setId(fareClassId);
    fareClass.setCode(fareCode);
    fareClass.setSeatSelectionPolicy(selectionPolicy);
    fareClass.setIncludedSeatCategory(includedSeatCategory);

    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(fareId);
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    return fare;
  }

  private AircraftLayoutEntity layout(Long layoutId, String columns) {
    AircraftLayoutEntity layout = new AircraftLayoutEntity();
    layout.setId(layoutId);
    layout.setColumns(columns);
    return layout;
  }

  private AircraftLayoutZoneEntity zone(String seatClassCode, int rowStart, int rowEnd) {
    SeatClassEntity seatClass = new SeatClassEntity();
    seatClass.setCode(seatClassCode);
    AircraftLayoutZoneEntity zone = new AircraftLayoutZoneEntity();
    zone.setSeatClass(seatClass);
    zone.setRowStart(rowStart);
    zone.setRowEnd(rowEnd);
    return zone;
  }

  private FlightSeatEntity seat(
      Long seatId,
      String seatNumber,
      String seatClassCode,
      String seatTypeCode,
      String seatCategoryCode,
      String status,
      String positionSurchargeAmount,
      String categorySurchargeAmount) {
    SeatClassEntity seatClass = new SeatClassEntity();
    seatClass.setCode(seatClassCode);

    SeatTypeEntity seatType = new SeatTypeEntity();
    seatType.setCode(seatTypeCode);
    seatType.setSurchargeAmount(new BigDecimal(positionSurchargeAmount));

    SeatCategoryEntity seatCategory = new SeatCategoryEntity();
    seatCategory.setCode(seatCategoryCode);
    seatCategory.setSurchargeAmount(new BigDecimal(categorySurchargeAmount));
    seatCategory.setPricingRank(
        switch (seatCategoryCode) {
          case "STANDARD" -> 1;
          case "PREFERRED" -> 2;
          case "EXIT_ROW" -> 3;
          case "EXTRA_LEGROOM" -> 4;
          default -> 99;
        });

    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setId(seatId);
    seat.setSeatNumber(seatNumber);
    seat.setSeatClass(seatClass);
    seat.setSeatType(seatType);
    seat.setSeatCategory(seatCategory);
    seat.setStatus(status);
    return seat;
  }

  private SeatCategoryEntity includedSeatCategory(String code, int pricingRank) {
    SeatCategoryEntity seatCategory = new SeatCategoryEntity();
    seatCategory.setCode(code);
    seatCategory.setPricingRank(pricingRank);
    return seatCategory;
  }
}
