package com.example.moduleai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolResult;
import com.example.moduleai.tool.core.ToolResultStatus;
import com.example.modulebooking.application.BookingSeatAssignmentService;
import com.example.modulebooking.application.FlightSeatMapService;
import com.example.modulepayment.application.SeatChangeSettlementService;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SeatToolsTest {

  @Test
  void getSeatMapMapsInputAndServiceView() {
    FlightSeatMapService seatMapService = Mockito.mock(FlightSeatMapService.class);
    FlightSeatMapService.FlightSeatMapView serviceView =
        new FlightSeatMapService.FlightSeatMapView(
            11L,
            22L,
            33L,
            "ECONOMY",
            "PAID",
            "STANDARD",
            "VND",
            new FlightSeatMapService.AircraftLayoutView(
                List.of("A", "B", "C"),
                List.of("C"),
                List.of(new FlightSeatMapService.SeatClassZoneView("ECONOMY", 1, 20))),
            new FlightSeatMapService.LegendView(
                List.of("AVAILABLE"), List.of("WINDOW"), List.of("STANDARD")),
            List.of());
    when(seatMapService.getSeatMap(11L, 22L)).thenReturn(serviceView);

    SeatTools tools =
        new SeatTools(
            new ToolExecutionGuard(Validation.buildDefaultValidatorFactory().getValidator()),
            seatMapService,
            Mockito.mock(BookingSeatAssignmentService.class),
            Mockito.mock(SeatChangeSettlementService.class));

    ToolResult<SeatTools.SeatMapOutput> result =
        tools.get_seat_map(new SeatTools.GetSeatMapInput(11L, 22L), null);

    assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
    assertThat(result.data().flightId()).isEqualTo(11L);
    assertThat(result.data().layout().columns()).containsExactly("A", "B", "C");
    assertThat(result.data().legend().statuses()).containsExactly("AVAILABLE");
    verify(seatMapService).getSeatMap(11L, 22L);
  }

  @Test
  void getSeatMapRejectsMissingFlightIdBeforeCallingService() {
    FlightSeatMapService seatMapService = Mockito.mock(FlightSeatMapService.class);
    SeatTools tools =
        new SeatTools(
            new ToolExecutionGuard(Validation.buildDefaultValidatorFactory().getValidator()),
            seatMapService,
            Mockito.mock(BookingSeatAssignmentService.class),
            Mockito.mock(SeatChangeSettlementService.class));

    ToolResult<SeatTools.SeatMapOutput> result =
        tools.get_seat_map(new SeatTools.GetSeatMapInput(null, 22L), null);

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error().code()).isEqualTo("VALIDATION_FAILED");
    assertThat(result.error().message()).contains("flightId");
    Mockito.verifyNoInteractions(seatMapService);
  }
}
