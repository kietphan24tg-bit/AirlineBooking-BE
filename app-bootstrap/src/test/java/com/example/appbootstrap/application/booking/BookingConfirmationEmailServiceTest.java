package com.example.moduleweb.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleweb.config.MailProperties;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.modulepayment.repository.PaymentRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class BookingConfirmationEmailServiceTest {

  @Mock private JavaMailSender mailSender;
  @Mock private BookingRepository bookingRepository;
  @Mock private BookingContactRepository bookingContactRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  private BookingConfirmationEmailService service;

  @BeforeEach
  void setUp() {
    service =
        new BookingConfirmationEmailService(
            mailSender,
            new MailProperties("no-reply@example.com"),
            bookingRepository,
            bookingContactRepository,
            ticketRepository,
            paymentRepository,
            bookingServiceRepository,
            passengerSeatAssignmentRepository);
  }

  @Test
  void shouldSendConfirmationEmailToPrimaryBookingContactAndMarkBooking() throws Exception {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(
            List.of(
                contact("Guest@example.com", true),
                contact("buyer@example.com", false),
                contact(" guest@example.com ", false),
                contact(" ", false)));
    when(paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(10L, List.of("SUCCESS")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(10L, List.of("BOOKED")))
        .thenReturn(List.of());
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    service.sendIfNeeded(10L);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage message = captor.getValue();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    message.writeTo(output);
    String html = output.toString(StandardCharsets.UTF_8);

    assertEquals("no-reply@example.com", message.getFrom()[0].toString());
    assertEquals("guest@example.com", message.getAllRecipients()[0].toString());
    assertEquals("The Sovereign Booking Confirmation - PNR ABC123", message.getSubject());
    assertTrue(html.contains("The Sovereign"));
    assertTrue(html.contains("Booking confirmed!"));
    assertTrue(html.contains("Thank you, NGUYEN, An"));
    assertTrue(html.contains("Booking Code (PNR)"));
    assertTrue(html.contains("Payment Confirmed At"));
    assertTrue(html.contains("CONTACT DETAILS"));
    assertTrue(html.contains("PASSENGER DETAILS"));
    assertTrue(html.contains("FLIGHT DETAILS"));
    assertTrue(html.contains("FARE BREAKDOWN"));
    assertTrue(html.contains("PAYMENT SUMMARY"));
    assertTrue(html.contains("PAYMENT DETAILS"));
    assertTrue(html.contains("ABC123"));
    assertTrue(html.contains("NGUYEN, An"));
    assertTrue(html.contains("12A"));
    assertTrue(html.contains("color:#7a4f17"));
    assertTrue(!html.contains("ANCILLARIES & ADD-ONS"));
    assertTrue(!html.contains("Seat / services"));
    assertNotNull(booking.getTicketEmailSentAt());
    verify(bookingRepository).save(booking);
  }

  @Test
  void shouldRenderAncillarySectionWhenSeatSurchargeExists() throws Exception {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking);
    PassengerSeatAssignmentEntity seatAssignment = seatAssignment(booking, ticket, "5B", "40000");
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of(contact("guest@example.com", true)));
    when(paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(10L, List.of("SUCCESS")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(10L, List.of("BOOKED")))
        .thenReturn(List.of(seatAssignment));
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    service.sendIfNeeded(10L);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    captor.getValue().writeTo(output);
    String html = output.toString(StandardCharsets.UTF_8);

    assertTrue(html.contains("ANCILLARIES & ADD-ONS"));
    assertTrue(html.contains("Seat selection"));
    assertTrue(html.contains("5B"));
    assertTrue(html.contains("40.000 VND"));
    assertTrue(html.contains("PAYMENT SUMMARY"));
  }

  @Test
  void shouldFallbackToBookingContactEmailWhenNoBookingContactEmailExists() throws Exception {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of(contact(null, true)));
    when(paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(10L, List.of("SUCCESS")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(10L, List.of("BOOKED")))
        .thenReturn(List.of());
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    service.sendIfNeeded(10L);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    assertEquals("guest@example.com", captor.getValue().getAllRecipients()[0].toString());
  }

  @Test
  void shouldFallbackToBookingContactEmailWhenPrimaryContactHasNoEmail() throws Exception {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of(contact(" ", true), contact("buyer@example.com", false)));
    when(paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(10L, List.of("SUCCESS")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(10L, List.of("BOOKED")))
        .thenReturn(List.of());
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

    service.sendIfNeeded(10L);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    assertEquals("guest@example.com", captor.getValue().getAllRecipients()[0].toString());
  }

  @Test
  void shouldSkipWhenEmailAlreadySent() {
    BookingEntity booking = booking();
    booking.setTicketEmailSentAt(OffsetDateTime.parse("2026-04-30T10:00:00Z"));
    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

    service.sendIfNeeded(10L);

    verify(mailSender, never()).send(any(MimeMessage.class));
    verify(ticketRepository, never()).findByBookingIdWithDetails(10L);
  }

  private BookingContactEntity contact(String email, boolean primary) {
    BookingContactEntity contact = new BookingContactEntity();
    contact.setEmail(email);
    contact.setPrimary(primary);
    return contact;
  }

  private BookingEntity booking() {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrCode("ABC123");
    booking.setContactEmail("guest@example.com");
    booking.setContactPhone("0901234567");
    booking.setTotalAmount(new BigDecimal("1200000"));
    booking.setCurrency("VND");
    booking.setCreatedAt(OffsetDateTime.parse("2026-04-30T09:00:00+07:00"));
    booking.setUpdatedAt(OffsetDateTime.parse("2026-04-30T09:00:00Z"));
    return booking;
  }

  private TicketEntity ticket(BookingEntity booking) {
    AirportEntity departure = new AirportEntity();
    departure.setCode("SGN");
    departure.setCity("Ho Chi Minh");
    AirportEntity arrival = new AirportEntity();
    arrival.setCode("HAN");
    arrival.setCity("Ha Noi");

    FlightEntity flight = new FlightEntity();
    flight.setId(55L);
    flight.setFlightNumber("VN123");
    flight.setFlightDate(LocalDate.parse("2026-05-01"));
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTerminal("T1");
    flight.setArrivalTerminal("T1");
    flight.setDepartureTime(OffsetDateTime.parse("2026-05-01T08:30:00+07:00"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-05-01T10:40:00+07:00"));

    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(99L);
    passenger.setFirstName("An");
    passenger.setLastName("NGUYEN");

    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setSeatNumber("12A");

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode("Eco");
    fareClass.setName("Economy");

    FlightFareEntity flightFare = new FlightFareEntity();
    flightFare.setBasePrice(new BigDecimal("1000000"));
    flightFare.setTotalTaxAmount(new BigDecimal("200000"));
    flightFare.setTotalPrice(new BigDecimal("1200000"));

    TicketEntity ticket = new TicketEntity();
    ticket.setBooking(booking);
    ticket.setTicketNumber("ABC123-1");
    ticket.setPassenger(passenger);
    ticket.setFlight(flight);
    ticket.setFlightSeat(seat);
    ticket.setFareClass(fareClass);
    ticket.setFlightFare(flightFare);
    return ticket;
  }

  private PassengerSeatAssignmentEntity seatAssignment(
      BookingEntity booking, TicketEntity ticket, String seatNumber, String surchargeAmount) {
    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setFlight(ticket.getFlight());
    seat.setSeatNumber(seatNumber);

    PassengerSeatAssignmentEntity assignment = new PassengerSeatAssignmentEntity();
    assignment.setBooking(booking);
    assignment.setPassenger(ticket.getPassenger());
    assignment.setFlightSeat(seat);
    assignment.setStatus("BOOKED");
    assignment.setSurchargeAmount(new BigDecimal(surchargeAmount));
    assignment.setCurrency("VND");
    assignment.setCreatedAt(OffsetDateTime.parse("2026-04-30T09:00:00Z"));
    assignment.setUpdatedAt(OffsetDateTime.parse("2026-04-30T09:00:00Z"));
    return assignment;
  }
}
