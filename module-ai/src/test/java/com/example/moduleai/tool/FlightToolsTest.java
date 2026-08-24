package com.example.moduleai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolResult;
import com.example.moduleai.tool.core.ToolResultStatus;
import com.example.moduleai.tool.support.PromotionCatalogToolService;
import com.example.modulebooking.application.BookingRevalidateService;
import com.example.modulebooking.application.PassengerSummaryCommand;
import com.example.modulebooking.application.RevalidateFareCommand;
import com.example.modulebooking.application.RevalidateFareView;
import com.example.modulebooking.application.RevalidateItineraryCommand;
import com.example.modulebooking.application.RevalidateItineraryView;
import com.example.moduleflight.application.FareFamilyCatalogService;
import com.example.moduleflight.application.FlightCalendarSearchService;
import com.example.moduleflight.application.FlightSearchFacadeService;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FlightToolsTest {

  @Test
  void revalidateFareMapsInputAndServiceView() {
    BookingRevalidateService service = Mockito.mock(BookingRevalidateService.class);
    when(service.revalidate(
            new RevalidateFareCommand(11L, 101L, "SAVE10", new PassengerSummaryCommand(1, 0, 0))))
        .thenReturn(
            new RevalidateFareView(
                "RV_123",
                900,
                11L,
                101L,
                "SAVE10",
                new RevalidateFareView.PassengerSummaryView(1, 0, 0),
                new RevalidateFareView.PricingView(
                    "VND",
                    new BigDecimal("1200000"),
                    new BigDecimal("300000"),
                    BigDecimal.ZERO,
                    new BigDecimal("1500000"),
                    new BigDecimal("150000"),
                    10,
                    new BigDecimal("1350000"))));
    FlightTools tools = flightTools(service);

    ToolResult<FlightTools.RevalidateFareOutput> result =
        tools.revalidate_fare(
            new FlightTools.RevalidateFareInput(
                11L, 101L, "SAVE10", new FlightTools.PassengerSummaryInput(1, 0, 0)),
            null);

    assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
    assertThat(result.data().revalidateId()).isEqualTo("RV_123");
    assertThat(result.data().pricing().discountPercent()).isEqualTo(10);
    verify(service)
        .revalidate(
            new RevalidateFareCommand(11L, 101L, "SAVE10", new PassengerSummaryCommand(1, 0, 0)));
  }

  @Test
  void revalidateItineraryRejectsBlankTripType() {
    FlightTools tools = flightTools(Mockito.mock(BookingRevalidateService.class));

    ToolResult<FlightTools.RevalidateItineraryOutput> result =
        tools.revalidate_itinerary(
            new FlightTools.RevalidateItineraryInput(
                " ",
                null,
                new FlightTools.PassengerSummaryInput(1, 0, 0),
                List.of(new FlightTools.SegmentInput("OUTBOUND", 11L, 101L))),
            null);

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error().code()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  void revalidateItineraryMapsSegments() {
    BookingRevalidateService service = Mockito.mock(BookingRevalidateService.class);
    when(service.revalidateItinerary(Mockito.any(RevalidateItineraryCommand.class)))
        .thenReturn(
            new RevalidateItineraryView(
                "RV_456",
                900,
                "ROUND_TRIP",
                null,
                new RevalidateItineraryView.PassengerSummaryView(1, 0, 0),
                new RevalidateItineraryView.PricingView(
                    "VND",
                    new BigDecimal("2600000"),
                    new BigDecimal("700000"),
                    BigDecimal.ZERO,
                    new BigDecimal("3300000"),
                    BigDecimal.ZERO,
                    null,
                    new BigDecimal("3300000")),
                List.of(
                    new RevalidateItineraryView.SegmentView(
                        "OUTBOUND",
                        11L,
                        101L,
                        5L,
                        "ECONOMY",
                        "Economy",
                        "ECONOMY",
                        "VND",
                        new RevalidateItineraryView.PricingView(
                            "VND",
                            new BigDecimal("1200000"),
                            new BigDecimal("300000"),
                            BigDecimal.ZERO,
                            new BigDecimal("1500000"),
                            BigDecimal.ZERO,
                            null,
                            new BigDecimal("1500000"))))));
    FlightTools tools = flightTools(service);

    ToolResult<FlightTools.RevalidateItineraryOutput> result =
        tools.revalidate_itinerary(
            new FlightTools.RevalidateItineraryInput(
                "ROUND_TRIP",
                null,
                new FlightTools.PassengerSummaryInput(1, 0, 0),
                List.of(
                    new FlightTools.SegmentInput("OUTBOUND", 11L, 101L),
                    new FlightTools.SegmentInput("INBOUND", 22L, 202L))),
            null);

    assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
    assertThat(result.data().tripType()).isEqualTo("ROUND_TRIP");
    assertThat(result.data().segments()).hasSize(1);
  }

  private FlightTools flightTools(BookingRevalidateService service) {
    return new FlightTools(
        new ToolExecutionGuard(Validation.buildDefaultValidatorFactory().getValidator()),
        Mockito.mock(FlightSearchFacadeService.class),
        Mockito.mock(FlightCalendarSearchService.class),
        Mockito.mock(FareFamilyCatalogService.class),
        Mockito.mock(PromotionCatalogToolService.class),
        service);
  }
}
