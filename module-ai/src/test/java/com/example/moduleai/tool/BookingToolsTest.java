package com.example.moduleai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolResult;
import com.example.moduleai.tool.core.ToolResultStatus;
import com.example.modulebooking.application.BookingLookupCommand;
import com.example.modulebooking.application.BookingLookupService;
import com.example.modulebooking.application.BookingLookupView;
import com.example.modulebooking.application.BookingManageOptionsService;
import com.example.modulebooking.application.BookingRevalidateService;
import com.example.modulebooking.application.CreateBookingCommand;
import com.example.modulebooking.application.CreateBookingView;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.modulebooking.application.UserTripDetailService;
import com.example.modulebooking.application.UserTripHistoryService;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BookingToolsTest {

  @Test
  void lookupBookingMapsInputAndServiceView() {
    BookingLookupService lookupService = Mockito.mock(BookingLookupService.class);
    BookingLookupView view = bookingLookupView();
    when(lookupService.lookup(new BookingLookupCommand("abc123", "Nguyen"))).thenReturn(view);
    BookingTools tools = bookingTools(lookupService);

    ToolResult<BookingTools.LookupBookingOutput> result =
        tools.lookup_booking(new BookingTools.LookupBookingInput("abc123", "Nguyen"), null);

    assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
    assertThat(result.data().booking().bookingId()).isEqualTo(10L);
    assertThat(result.data().booking().pnrCode()).isEqualTo("ABC123");
    assertThat(result.data().actions().canSelectSeat()).isTrue();
    verify(lookupService).lookup(new BookingLookupCommand("abc123", "Nguyen"));
  }

  @Test
  void lookupBookingRejectsBlankPnrBeforeCallingService() {
    BookingLookupService lookupService = Mockito.mock(BookingLookupService.class);
    BookingTools tools = bookingTools(lookupService);

    ToolResult<BookingTools.LookupBookingOutput> result =
        tools.lookup_booking(new BookingTools.LookupBookingInput(" ", "Nguyen"), null);

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error().code()).isEqualTo("VALIDATION_FAILED");
    assertThat(result.error().message()).contains("pnrCode");
    Mockito.verifyNoInteractions(lookupService);
  }

  @Test
  void createBookingUsesTrustedContextForConfirmationAndIdempotency() {
    BookingLookupService lookupService = Mockito.mock(BookingLookupService.class);
    BookingRevalidateService revalidateService = Mockito.mock(BookingRevalidateService.class);
    when(revalidateService.createBooking(
            Mockito.eq(77L),
            Mockito.argThat(
                command ->
                    command.revalidateId().equals("RV_123")
                        && command.idempotencyKey().equals("idem-123")
                        && command.passengerSummary().adult().equals(1)
                        && command.contacts().size() == 1
                        && command.passengers().size() == 1)))
        .thenReturn(createBookingView());
    BookingTools tools = bookingTools(lookupService, revalidateService);

    org.springframework.ai.chat.model.ToolContext toolContext =
        new org.springframework.ai.chat.model.ToolContext(
            java.util.Map.of("userId", 77L, "confirmed", true, "idempotencyKey", "idem-123"));

    ToolResult<BookingTools.CreateBookingOutput> result =
        tools.create_booking(
            new BookingTools.CreateBookingInput(
                "RV_123",
                new BookingTools.PassengerSummaryInput(1, 0, 0),
                List.of(new BookingTools.ContactInput("guest@example.com", "0900000000", true)),
                List.of(
                    new BookingTools.PassengerInput(
                        "An",
                        "Nguyen",
                        LocalDate.parse("1998-01-01"),
                        "male",
                        "VN",
                        "adult",
                        "passport",
                        "B1234567",
                        LocalDate.parse("2030-01-01")))),
            toolContext);

    assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
    assertThat(result.data().booking().bookingId()).isEqualTo(55L);
    verify(revalidateService)
        .createBooking(Mockito.eq(77L), Mockito.any(CreateBookingCommand.class));
  }

  @Test
  void createBookingRejectsMissingTrustedIdempotencyKey() {
    BookingTools tools =
        bookingTools(
            Mockito.mock(BookingLookupService.class), Mockito.mock(BookingRevalidateService.class));

    ToolResult<BookingTools.CreateBookingOutput> result =
        tools.create_booking(
            new BookingTools.CreateBookingInput(
                "RV_123",
                new BookingTools.PassengerSummaryInput(1, 0, 0),
                List.of(new BookingTools.ContactInput("guest@example.com", null, true)),
                List.of(
                    new BookingTools.PassengerInput(
                        "An",
                        "Nguyen",
                        LocalDate.parse("1998-01-01"),
                        "male",
                        "VN",
                        "adult",
                        "passport",
                        "B1234567",
                        LocalDate.parse("2030-01-01")))),
            new org.springframework.ai.chat.model.ToolContext(java.util.Map.of("confirmed", true)));

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error().code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
  }

  private BookingTools bookingTools(
      BookingLookupService lookupService, BookingRevalidateService revalidateService) {
    return new BookingTools(
        new ToolExecutionGuard(Validation.buildDefaultValidatorFactory().getValidator()),
        lookupService,
        revalidateService,
        Mockito.mock(TicketIssuanceService.class),
        Mockito.mock(UserTripHistoryService.class),
        Mockito.mock(UserTripDetailService.class),
        Mockito.mock(BookingManageOptionsService.class));
  }

  private BookingTools bookingTools(BookingLookupService lookupService) {
    return bookingTools(lookupService, Mockito.mock(BookingRevalidateService.class));
  }

  private BookingLookupView bookingLookupView() {
    return new BookingLookupView(
        new BookingLookupView.BookingSummary(
            10L,
            "ABC123",
            "CONFIRMED",
            "Confirmed",
            new BigDecimal("1200000.00"),
            "VND",
            null,
            null),
        new BookingLookupView.PrimaryContact("guest@example.com", "0900000000"),
        new BookingLookupView.PaymentDetails(
            new BookingLookupView.PriceBreakdown(
                new BigDecimal("1000000.00"),
                BigDecimal.ZERO,
                new BigDecimal("200000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1200000.00"),
                "VND")),
        List.of(),
        List.of(),
        new BookingLookupView.Actions(true, true, true, true, true));
  }

  private CreateBookingView createBookingView() {
    return new CreateBookingView(
        new CreateBookingView.BookingView(55L, "ABC123", "PENDING_PAYMENT", null, null),
        new CreateBookingView.FlightView(
            11L,
            "VN123",
            LocalDate.parse("2026-05-01"),
            "SCHEDULED",
            new CreateBookingView.AirlineView(1L, "VN", "Vietnam Airlines", "logo"),
            new CreateBookingView.FlightEndpointView(
                1L,
                "SGN",
                "Tan Son Nhat",
                "Ho Chi Minh City",
                "Vietnam",
                "Asia/Ho_Chi_Minh",
                "T1",
                "A1",
                null),
            new CreateBookingView.FlightEndpointView(
                2L, "HAN", "Noi Bai", "Hanoi", "Vietnam", "Asia/Ho_Chi_Minh", "T2", "B1", null)),
        new CreateBookingView.FareView(
            101L,
            5L,
            "AVAILABLE",
            "ECONOMY",
            "Economy",
            "ECONOMY",
            new BigDecimal("1200000.00"),
            new BigDecimal("300000.00"),
            new BigDecimal("1500000.00"),
            "VND",
            23,
            7,
            false,
            true,
            new BigDecimal("300000.00"),
            new BigDecimal("500000.00")),
        new CreateBookingView.PricingView(
            "VND",
            new BigDecimal("1200000.00"),
            new BigDecimal("300000.00"),
            BigDecimal.ZERO,
            new BigDecimal("1500000.00"),
            BigDecimal.ZERO,
            null,
            new BigDecimal("1500000.00")),
        new CreateBookingView.PromotionView(null, null, BigDecimal.ZERO, null),
        List.of(new CreateBookingView.ContactView(1L, "guest@example.com", "0900000000", true)),
        new CreateBookingView.PassengerSummaryView(1, 0, 0),
        List.of(
            new CreateBookingView.PassengerView(
                10L,
                "An",
                "Nguyen",
                LocalDate.parse("1998-01-01"),
                "MALE",
                "VN",
                "ADULT",
                "PASSPORT",
                "B1234567",
                LocalDate.parse("2030-01-01"))),
        new CreateBookingView.SeatReservationView(1, "HELD", null),
        new CreateBookingView.NextActionView("CREATE_PAYMENT", "/api/payments"));
  }
}
