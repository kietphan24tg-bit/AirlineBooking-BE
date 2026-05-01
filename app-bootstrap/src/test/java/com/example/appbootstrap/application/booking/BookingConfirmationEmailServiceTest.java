package com.example.appbootstrap.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.config.MailProperties;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class BookingConfirmationEmailServiceTest {

  @Mock private JavaMailSender mailSender;
  @Mock private BookingRepository bookingRepository;
  @Mock private BookingContactRepository bookingContactRepository;
  @Mock private TicketRepository ticketRepository;

  private BookingConfirmationEmailService service;

  @BeforeEach
  void setUp() {
    service =
        new BookingConfirmationEmailService(
            mailSender,
            new MailProperties("no-reply@example.com"),
            bookingRepository,
            bookingContactRepository,
            ticketRepository);
  }

  @Test
  void shouldSendConfirmationEmailToAllBookingContactsAndMarkBooking() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking);
    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(
            List.of(
                contact("Guest@example.com", true),
                contact("buyer@example.com", false),
                contact(" guest@example.com ", false),
                contact(" ", false)));

    service.sendIfNeeded(10L);

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    SimpleMailMessage message = captor.getValue();
    assertEquals("no-reply@example.com", message.getFrom());
    assertEquals(List.of("guest@example.com", "buyer@example.com"), List.of(message.getTo()));
    assertEquals("Your booking is confirmed - PNR ABC123", message.getSubject());
    assertTrue(message.getText().contains("PNR: ABC123"));
    assertTrue(message.getText().contains("Ticket: ABC123-1"));
    assertTrue(message.getText().contains("Seat: 12A"));
    assertTrue(booking.getTicketEmailSentAt() != null);
    verify(bookingRepository).save(booking);
  }

  @Test
  void shouldFallbackToBookingContactEmailWhenNoBookingContactEmailExists() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking);
    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of(contact(null, true)));

    service.sendIfNeeded(10L);

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    assertEquals(List.of("guest@example.com"), List.of(captor.getValue().getTo()));
  }

  @Test
  void shouldSkipWhenEmailAlreadySent() {
    BookingEntity booking = booking();
    booking.setTicketEmailSentAt(OffsetDateTime.parse("2026-04-30T10:00:00Z"));
    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

    service.sendIfNeeded(10L);

    verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
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
    booking.setTotalAmount(new BigDecimal("1200000"));
    booking.setCurrency("VND");
    booking.setUpdatedAt(OffsetDateTime.parse("2026-04-30T09:00:00Z"));
    return booking;
  }

  private TicketEntity ticket(BookingEntity booking) {
    AirportEntity departure = new AirportEntity();
    departure.setCode("SGN");
    AirportEntity arrival = new AirportEntity();
    arrival.setCode("HAN");

    FlightEntity flight = new FlightEntity();
    flight.setFlightNumber("VN123");
    flight.setFlightDate(LocalDate.parse("2026-05-01"));
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTime(OffsetDateTime.parse("2026-05-01T08:30:00+07:00"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-05-01T10:40:00+07:00"));

    PassengerEntity passenger = new PassengerEntity();
    passenger.setFirstName("An");
    passenger.setLastName("Nguyen");

    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setSeatNumber("12A");

    TicketEntity ticket = new TicketEntity();
    ticket.setBooking(booking);
    ticket.setTicketNumber("ABC123-1");
    ticket.setPassenger(passenger);
    ticket.setFlight(flight);
    ticket.setFlightSeat(seat);
    return ticket;
  }
}
