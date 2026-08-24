package com.example.moduleweb.application.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.modulebooking.entity.BoardingPassEntity;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BoardingPassRepository;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulecheckin.application.CheckInConfirmView;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleweb.config.MailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class CheckInSuccessEmailServiceTest {

  @Mock private JavaMailSender mailSender;
  @Mock private BookingContactRepository bookingContactRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private BoardingPassRepository boardingPassRepository;

  private CheckInSuccessEmailService service;

  @BeforeEach
  void setUp() {
    service =
        new CheckInSuccessEmailService(
            mailSender,
            new MailProperties("no-reply@example.com"),
            bookingContactRepository,
            ticketRepository,
            boardingPassRepository,
            "http://localhost:5173");
  }

  @Test
  void shouldSendCheckInSuccessEmailWithBoardingPassMarkup() throws Exception {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking);
    BoardingPassEntity savedBoardingPass = boardingPass(ticket);
    CheckInConfirmView confirmView = confirmView();
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of(contact("guest@example.com", true)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(88L)))
        .thenReturn(List.of(ticket));
    when(boardingPassRepository.findByTicketIdIn(List.of(88L)))
        .thenReturn(List.of(savedBoardingPass));
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    service.sendSuccess(confirmView);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage message = captor.getValue();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    message.writeTo(output);
    String html = output.toString(StandardCharsets.UTF_8);

    assertEquals("no-reply@example.com", message.getFrom()[0].toString());
    assertEquals("guest@example.com", message.getAllRecipients()[0].toString());
    assertEquals("The Sovereign Check-in Successful - PNR PNRE003B0A8", message.getSubject());
    assertTrue(html.contains("Check-in successful!"));
    assertTrue(html.contains("Flight information"));
    assertTrue(html.contains("Passenger & boarding pass"));
    assertTrue(html.contains("PHAN VAN A"));
    assertTrue(html.contains("HAN"));
    assertTrue(html.contains("SGN"));
    assertTrue(html.contains("5B"));
    assertTrue(html.contains("G1"));
    assertTrue(html.contains("001"));
    assertTrue(html.contains("chart.googleapis.com"));
    assertTrue(html.contains("/manage-booking/check-in/10/129"));
  }

  @Test
  void shouldSkipWhenNoRecipientsExist() {
    CheckInConfirmView confirmView = confirmView();
    BookingEntity booking = booking();
    booking.setContactEmail(" ");
    TicketEntity ticket = ticket(booking);

    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of(contact(" ", true)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(88L)))
        .thenReturn(List.of(ticket));

    service.sendSuccess(confirmView);

    verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
  }

  private CheckInConfirmView confirmView() {
    return new CheckInConfirmView(
        new CheckInConfirmView.BookingView(10L, "PNRE003B0A8", "CHECKED_IN_COMPLETED"),
        List.of(
            new CheckInConfirmView.SegmentView(
                129L,
                "0A52788",
                List.of(
                    new CheckInConfirmView.PassengerView(
                        55L, 88L, "TK-001", "PHAN VAN A", "5B", 901L, "CHECKED_IN")))),
        List.of(
            new CheckInConfirmView.BoardingPassView(
                901L,
                88L,
                55L,
                129L,
                "BP|PNRE003B0A8|129|55|88|5B|2026-06-06T21:45:00+07:00",
                "G1",
                OffsetDateTime.parse("2026-06-06T21:15:00+07:00"),
                1,
                "5B")),
        new CheckInConfirmView.SummaryView(1, 1));
  }

  private BookingContactEntity contact(String email, boolean primary) {
    BookingContactEntity contact = new BookingContactEntity();
    contact.setEmail(email);
    contact.setPrimary(primary);
    return contact;
  }

  private BoardingPassEntity boardingPass(TicketEntity ticket) {
    BoardingPassEntity entity = new BoardingPassEntity();
    entity.setId(901L);
    entity.setTicket(ticket);
    entity.setQrCode("BP|PNRE003B0A8|129|55|88|5B|2026-06-06T21:45:00+07:00");
    entity.setGate("G1");
    entity.setBoardingTime(OffsetDateTime.parse("2026-06-06T21:15:00+07:00"));
    entity.setSequenceNumber(1);
    entity.setSeatLabel("5B");
    entity.setCreatedAt(OffsetDateTime.parse("2026-06-06T18:00:00+07:00"));
    return entity;
  }

  private BookingEntity booking() {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrCode("PNRE003B0A8");
    booking.setContactEmail("guest@example.com");
    booking.setContactPhone("0901234567");
    booking.setCurrency("VND");
    booking.setTotalAmount(new BigDecimal("2035000"));
    booking.setCreatedAt(OffsetDateTime.parse("2026-06-05T09:00:00+07:00"));
    booking.setUpdatedAt(OffsetDateTime.parse("2026-06-05T09:00:00+07:00"));
    return booking;
  }

  private TicketEntity ticket(BookingEntity booking) {
    AirportEntity departure = new AirportEntity();
    departure.setCode("HAN");
    departure.setCity("Hanoi");
    departure.setTimeZone("Asia/Bangkok");

    AirportEntity arrival = new AirportEntity();
    arrival.setCode("SGN");
    arrival.setCity("Ho Chi Minh City");
    arrival.setTimeZone("Asia/Bangkok");

    FlightEntity flight = new FlightEntity();
    flight.setId(129L);
    flight.setFlightNumber("0A52788");
    flight.setFlightDate(LocalDate.parse("2026-06-06"));
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTerminal("1");
    flight.setDepartureGate("G1");
    flight.setDepartureTime(OffsetDateTime.parse("2026-06-06T21:45:00+07:00"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-06-07T00:25:00+07:00"));

    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(55L);
    passenger.setFirstName("VAN A");
    passenger.setLastName("PHAN");

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode("ECO");
    fareClass.setName("Economy");

    FlightFareEntity flightFare = new FlightFareEntity();
    flightFare.setBasePrice(new BigDecimal("1800000"));
    flightFare.setTotalTaxAmount(new BigDecimal("235000"));
    flightFare.setTotalPrice(new BigDecimal("2035000"));

    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setSeatNumber("5B");

    TicketEntity ticket = new TicketEntity();
    ticket.setId(88L);
    ticket.setBooking(booking);
    ticket.setTicketNumber("TK-001");
    ticket.setPassenger(passenger);
    ticket.setFlight(flight);
    ticket.setFareClass(fareClass);
    ticket.setFlightFare(flightFare);
    ticket.setFlightSeat(seat);
    return ticket;
  }
}
