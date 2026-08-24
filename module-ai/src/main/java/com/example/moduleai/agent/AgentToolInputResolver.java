package com.example.moduleai.agent;

import com.example.moduleai.config.AiAgentGuardrailProperties;
import com.example.moduleai.tool.AirportTools;
import com.example.moduleai.tool.BookingTools;
import com.example.moduleai.tool.FlightTools;
import com.example.moduleai.tool.PaymentTools;
import com.example.moduleai.tool.SeatTools;
import com.example.moduleflight.application.FlightSearchTripType;
import com.example.moduleflight.application.FlightSortBy;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AgentToolInputResolver {

  private static final Pattern PAYMENT_ID_PATTERN =
      Pattern.compile("(?i)\\bpayment(?:[_\\s-]?id)?\\s*[:#]?\\s*(\\d+)\\b");
  private static final Pattern BOOKING_ID_PATTERN =
      Pattern.compile("(?i)\\bbooking(?:[_\\s-]?id)?\\s*[:#]?\\s*(\\d+)\\b");
  private static final Pattern FLIGHT_ID_PATTERN =
      Pattern.compile("(?i)\\bflight(?:[_\\s-]?id)?\\s*[:#]?\\s*(\\d+)\\b");
  private static final Pattern FARE_ID_PATTERN =
      Pattern.compile("(?i)\\bfare(?:[_\\s-]?id)?\\s*[:#]?\\s*(\\d+)\\b");
  private static final Pattern PNR_PATTERN =
      Pattern.compile("(?i)\\bpnr\\s*[:#]?\\s*([A-Z0-9]{6})\\b");
  private static final Pattern LAST_NAME_PATTERN =
      Pattern.compile("(?i)\\blast\\s*name\\s*[:#]?\\s*([A-Za-z][A-Za-z\\s'-]{0,60})");
  private static final Pattern AIRPORT_QUERY_PATTERN =
      Pattern.compile("(?i)\\bairport\\s*[:#]?\\s*([A-Za-z0-9\\s-]{2,80})");
  private static final Pattern DATE_PATTERN = Pattern.compile("\\b(20\\d{2}-\\d{2}-\\d{2})\\b");
  private static final Pattern IATA_PATTERN = Pattern.compile("\\b([A-Z]{3})\\b");

  private final AiAgentGuardrailProperties properties;

  public AgentToolInputResolver(AiAgentGuardrailProperties properties) {
    this.properties = properties;
  }

  public Object resolve(String toolName, String message) {
    return switch (toolName) {
      case "lookup_booking" -> resolveLookupBooking(message);
      case "get_payment_detail" -> resolvePaymentId(message);
      case "list_payment_methods" -> resolveListPaymentMethods(message);
      case "get_seat_map" -> resolveSeatMap(message);
      case "revalidate_fare" -> resolveRevalidateFare(message);
      case "search_airports" -> resolveSearchAirports(message);
      case "search_flights" -> resolveSearchFlights(message);
      case "search_flight_calendar" -> resolveSearchFlightCalendar(message);
      case "get_booking_tickets" -> resolveBookingId(message);
      default -> null;
    };
  }

  private Object resolveLookupBooking(String message) {
    String pnr = capture(PNR_PATTERN, message);
    String lastName = capture(LAST_NAME_PATTERN, message);
    if (pnr == null || lastName == null) {
      return null;
    }
    return new BookingTools.LookupBookingInput(pnr.toUpperCase(Locale.ROOT), trimField(lastName));
  }

  private Object resolvePaymentId(String message) {
    Long paymentId = longValue(PAYMENT_ID_PATTERN, message);
    return paymentId == null ? null : new PaymentTools.PaymentIdInput(paymentId);
  }

  private Object resolveListPaymentMethods(String message) {
    Long bookingId = longValue(BOOKING_ID_PATTERN, message);
    return bookingId == null ? null : new PaymentTools.ListPaymentMethodsInput(bookingId, true);
  }

  private Object resolveSeatMap(String message) {
    Long flightId = longValue(FLIGHT_ID_PATTERN, message);
    Long fareId = longValue(FARE_ID_PATTERN, message);
    if (flightId == null || fareId == null) {
      return null;
    }
    return new SeatTools.GetSeatMapInput(flightId, fareId);
  }

  private Object resolveRevalidateFare(String message) {
    Long flightId = longValue(FLIGHT_ID_PATTERN, message);
    Long fareId = longValue(FARE_ID_PATTERN, message);
    if (flightId == null || fareId == null) {
      return null;
    }
    return new FlightTools.RevalidateFareInput(
        flightId, fareId, null, new FlightTools.PassengerSummaryInput(1, 0, 0));
  }

  private Object resolveSearchAirports(String message) {
    String query = capture(AIRPORT_QUERY_PATTERN, message);
    if (query == null) {
      return null;
    }
    return new AirportTools.SearchAirportsInput(trimField(query), 10);
  }

  private Object resolveSearchFlights(String message) {
    Matcher matcher = IATA_PATTERN.matcher(message.toUpperCase(Locale.ROOT));
    String from = null;
    String to = null;
    while (matcher.find()) {
      if (from == null) {
        from = matcher.group(1);
      } else {
        to = matcher.group(1);
        break;
      }
    }
    LocalDate departureDate = firstDate(message);
    if (from == null || to == null || departureDate == null) {
      return null;
    }
    return new FlightTools.SearchFlightsInput(
        FlightSearchTripType.ONE_WAY,
        from,
        to,
        departureDate,
        null,
        1,
        0,
        0,
        null,
        null,
        null,
        null,
        5,
        null,
        null,
        null,
        null,
        null,
        FlightSortBy.CHEAPEST);
  }

  private Object resolveSearchFlightCalendar(String message) {
    Matcher matcher = IATA_PATTERN.matcher(message.toUpperCase(Locale.ROOT));
    String from = null;
    String to = null;
    while (matcher.find()) {
      if (from == null) {
        from = matcher.group(1);
      } else {
        to = matcher.group(1);
        break;
      }
    }
    LocalDate departureDate = firstDate(message);
    if (from == null || to == null || departureDate == null) {
      return null;
    }
    return new FlightTools.SearchFlightCalendarInput(
        FlightSearchTripType.ONE_WAY,
        from,
        to,
        departureDate,
        1,
        0,
        0,
        null,
        15,
        null,
        null,
        null,
        null,
        null, // connectionFilter
        FlightSortBy.CHEAPEST,
        3,
        3);
  }

  private Object resolveBookingId(String message) {
    Long bookingId = longValue(BOOKING_ID_PATTERN, message);
    return bookingId == null ? null : new BookingTools.BookingIdInput(bookingId);
  }

  private String capture(Pattern pattern, String message) {
    Matcher matcher = pattern.matcher(message);
    if (!matcher.find()) {
      return null;
    }
    return matcher.group(1);
  }

  private Long longValue(Pattern pattern, String message) {
    String value = capture(pattern, message);
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private LocalDate firstDate(String message) {
    Matcher matcher = DATE_PATTERN.matcher(message);
    if (!matcher.find()) {
      return null;
    }
    try {
      return LocalDate.parse(matcher.group(1));
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private String trimField(String value) {
    String trimmed = value.trim();
    if (trimmed.length() > properties.getMaxStructuredFieldLength()) {
      return trimmed.substring(0, properties.getMaxStructuredFieldLength());
    }
    return trimmed;
  }
}
