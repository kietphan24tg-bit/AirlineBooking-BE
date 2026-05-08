package com.example.appbootstrap.controller.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.application.booking.BookingAncillarySelectionService;
import com.example.appbootstrap.application.booking.BookingLookupService;
import com.example.appbootstrap.application.booking.BookingManageOptionsService;
import com.example.appbootstrap.application.booking.BookingRevalidateService;
import com.example.appbootstrap.application.booking.BookingSeatAssignmentService;
import com.example.appbootstrap.controller.booking.dto.BookingCancellationPreviewRequest;
import com.example.appbootstrap.controller.booking.dto.BookingCancellationPreviewResponse;
import com.example.appbootstrap.controller.booking.dto.BookingCancellationRequest;
import com.example.appbootstrap.controller.booking.dto.BookingCancellationResponse;
import com.example.appbootstrap.controller.booking.dto.BookingManageOptionsResponse;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.Role;
import com.example.modulepayment.application.BookingCancellationPreviewView;
import com.example.modulepayment.application.BookingCancellationResultView;
import com.example.modulepayment.application.BookingCancellationService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingsControllerTest {

  @Mock private BookingRevalidateService bookingRevalidateService;
  @Mock private BookingLookupService bookingLookupService;
  @Mock private BookingManageOptionsService bookingManageOptionsService;
  @Mock private BookingSeatAssignmentService bookingSeatAssignmentService;
  @Mock private BookingAncillarySelectionService bookingAncillarySelectionService;
  @Mock private TicketIssuanceService ticketIssuanceService;
  @Mock private BookingCancellationService bookingCancellationService;

  private BookingsController controller;
  private Validator validator;

  @BeforeEach
  void setUp() {
    controller =
        new BookingsController(
            bookingRevalidateService,
            bookingLookupService,
            bookingManageOptionsService,
            bookingSeatAssignmentService,
            bookingAncillarySelectionService,
            ticketIssuanceService,
            bookingCancellationService);
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void getManageOptions_mapsServiceResponse() {
    when(bookingManageOptionsService.getManageOptions(null, 10L))
        .thenReturn(
            new BookingManageOptionsService.BookingManageOptionsView(
                10L,
                "PENDING_PAYMENT",
                "VND",
                List.of(
                    new BookingManageOptionsService.PassengerView(1L, "Van A", "Nguyen", "ADULT")),
                List.of(
                    new BookingManageOptionsService.ItineraryView(
                        100L,
                        1000L,
                        "VN123",
                        OffsetDateTime.parse("2026-06-01T08:30:00+07:00"),
                        OffsetDateTime.parse("2026-06-01T10:35:00+07:00"),
                        "SGN",
                        "HAN")),
                List.of(
                    new BookingManageOptionsService.SeatAssignmentView(
                        1L, 100L, 500L, "12A", "BOOKED", new BigDecimal("20000.00"), "VND")),
                List.of(
                    new BookingManageOptionsService.ServiceSelectionView(
                        1L, 20L, "MEAL", "Meals", 200L, "Pho", 1, new BigDecimal("120000.00")))));

    BookingManageOptionsResponse response = controller.getManageOptions(null, 10L);

    assertEquals(10L, response.bookingId());
    assertEquals("PENDING_PAYMENT", response.bookingStatus());
    assertEquals("VND", response.currency());
    assertEquals(1L, response.passengers().getFirst().passengerId());
    assertEquals(1000L, response.itineraries().getFirst().fareId());
    assertEquals("12A", response.seatAssignments().getFirst().seatNumber());
    assertEquals("Pho", response.serviceSelections().getFirst().optionName());
    verify(bookingManageOptionsService).getManageOptions(null, 10L);
  }

  @Test
  void previewCancellationShouldMapServiceResponse() {
    when(bookingCancellationService.preview(7L, 10L, List.of(101L)))
        .thenReturn(
            new BookingCancellationPreviewView(
                10L,
                "CONFIRMED",
                "SUCCESS",
                "VND",
                List.of(
                    new BookingCancellationPreviewView.ItemView(
                        1001L,
                        101L,
                        "ADULT",
                        "An Nguyen",
                        true,
                        null,
                        new BigDecimal("1000000"),
                        new BigDecimal("100000"),
                        new BigDecimal("900000"),
                        new BigDecimal("50000"),
                        new BigDecimal("20000"),
                        new BigDecimal("150000"),
                        new BigDecimal("820000"))),
                new BookingCancellationPreviewView.SummaryView(
                    new BigDecimal("1000000"),
                    new BigDecimal("100000"),
                    new BigDecimal("900000"),
                    new BigDecimal("50000"),
                    new BigDecimal("20000"),
                    new BigDecimal("150000"),
                    new BigDecimal("820000"))));

    BookingCancellationPreviewResponse response =
        controller.previewCancellation(
            principal(), 10L, new BookingCancellationPreviewRequest(List.of(101L)));

    assertEquals(10L, response.bookingId());
    assertEquals("SUCCESS", response.paymentStatus());
    assertEquals("An Nguyen", response.items().getFirst().passengerName());
    assertEquals(new BigDecimal("820000"), response.summary().netRefund());
  }

  @Test
  void cancelBookingShouldMapServiceResponse() {
    when(bookingCancellationService.cancel(
            7L,
            10L,
            List.of(101L),
            "Need to cancel",
            "idem-1",
            new BigDecimal("820000"),
            new BigDecimal("150000")))
        .thenReturn(
            new BookingCancellationResultView(
                10L,
                "ABC123",
                "user@example.com",
                900L,
                "SUCCEEDED",
                "PAYOS",
                "refund_123",
                "CONFIRMED",
                "PARTIALLY_REFUNDED",
                "VND",
                "Tráº¡ng thÃ¡i hoÃ n tiá»n",
                "Lá»‡nh hoÃ n tiá»n Ä‘Ã£ Ä‘Æ°á»£c tiáº¿p nháº­n vÃ  xá»­ lÃ½ 24/7.",
                "Thá»i gian nháº­n tiá»n thá»±c táº¿ phá»¥ thuá»™c vÃ o ngÃ¢n hÃ ng nháº­n.",
                List.of(
                    new BookingCancellationResultView.ItemView(
                        1001L,
                        101L,
                        "ADULT",
                        "An Nguyen",
                        "CANCELLED",
                        new BigDecimal("1000000"),
                        new BigDecimal("100000"),
                        new BigDecimal("900000"),
                        new BigDecimal("50000"),
                        new BigDecimal("20000"),
                        new BigDecimal("150000"),
                        new BigDecimal("820000"))),
                new BookingCancellationResultView.SummaryView(
                    new BigDecimal("1000000"),
                    new BigDecimal("100000"),
                    new BigDecimal("900000"),
                    new BigDecimal("50000"),
                    new BigDecimal("20000"),
                    new BigDecimal("150000"),
                    new BigDecimal("820000"))));

    BookingCancellationResponse response =
        controller.cancelBooking(
            principal(),
            10L,
            new BookingCancellationRequest(
                List.of(101L),
                "Need to cancel",
                "idem-1",
                new BigDecimal("820000"),
                new BigDecimal("150000")));

    assertEquals(10L, response.bookingId());
    assertEquals("ABC123", response.pnrCode());
    assertEquals(900L, response.cancellationId());
    assertEquals("SUCCEEDED", response.refundStatus());
    assertEquals("refund_123", response.providerRefundId());
    assertEquals("Tráº¡ng thÃ¡i hoÃ n tiá»n", response.refundEstimateTitle());
    assertEquals(
        "Lá»‡nh hoÃ n tiá»n Ä‘Ã£ Ä‘Æ°á»£c tiáº¿p nháº­n vÃ  xá»­ lÃ½ 24/7.",
        response.refundEstimateMessage());
    assertEquals(
        "Thá»i gian nháº­n tiá»n thá»±c táº¿ phá»¥ thuá»™c vÃ o ngÃ¢n hÃ ng nháº­n.",
        response.refundEstimateNote());
    assertEquals("CANCELLED", response.items().getFirst().ticketStatus());
  }

  @Test
  void cancellationRequestsShouldFailValidationWhenRequiredFieldsAreMissing() {
    var previewViolations = validator.validate(new BookingCancellationPreviewRequest(List.of()));
    var cancelViolations =
        validator.validate(
            new BookingCancellationRequest(List.of(), null, " ", null, new BigDecimal("-1")));

    assertEquals(1, previewViolations.size());
    assertEquals(4, cancelViolations.size());
  }

  private static AppUserPrincipal principal() {
    return new AppUserPrincipal(7L, "user@example.com", "secret", Role.USER, null);
  }
}
