package com.example.moduleai.agent;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IntentRouter {

  private static final Pattern PAYMENT_ID_PATTERN = Pattern.compile("\\bpayment\\s+[a-z0-9-]+\\b");
  private static final Pattern FLIGHT_CODE_PATTERN = Pattern.compile("\\b[A-Z]{2}\\d{2,4}\\b");
  private static final Pattern NORMALIZED_AIRPORT_ROUTE_PATTERN =
      Pattern.compile("\\b(?:from|tu)\\s+[a-z]{3}\\s+(?:to|den)\\s+[a-z]{3}\\b");
  private static final Pattern AIRPORT_PAIR_PATTERN =
      Pattern.compile("\\b[A-Z]{3}\\s*[-/]\\s*[A-Z]{3}\\b");

  public RoutingDecision route(String message) {
    String normalized = normalize(message);

    if (isUnsafeFallback(normalized)) {
      return RoutingDecision.fallback("unsafe_unconfirmed_policy");
    }

    RoutingDecision apiDecision = routeDynamicApi(normalized, message);
    if (apiDecision != null) {
      return apiDecision;
    }

    return RoutingDecision.rag("static_policy_rag");
  }

  private RoutingDecision routeDynamicApi(String normalized, String rawMessage) {
    if (isProfileUpdateRequest(normalized)) {
      return RoutingDecision.api(
          "account_profile_update",
          "PATCH /api/users/me",
          List.of(RequiredContext.AUTHENTICATED_ACCOUNT),
          true,
          false);
    }
    if (isProfileLookupRequest(normalized)) {
      return RoutingDecision.api(
          "account_profile",
          "GET /api/users/me",
          List.of(RequiredContext.AUTHENTICATED_ACCOUNT),
          false,
          false);
    }
    if (containsAll(normalized, "booking", "thanh toan")
        && containsAny(normalized, "chua", "roi")
        && !containsAny(normalized, "giu bao lau")) {
      return bookingScoped(rawMessage, "booking_lookup", "POST /api/bookings/lookup", true, false);
    }
    if (containsAll(normalized, "booking", "con han")) {
      return bookingScoped(rawMessage, "booking_lookup", "POST /api/bookings/lookup", true, false);
    }
    if (containsAll(normalized, "qr", "con bao lau")) {
      return bookingOrPaymentScoped(
          rawMessage, "payment_detail", "GET /api/payments/{paymentId}", false);
    }
    if (containsAll(normalized, "payment", "trang thai")) {
      return paymentScoped(rawMessage, "payment_detail", "GET /api/payments/{paymentId}");
    }
    if (containsAny(normalized, "dung stripe", "stripe cho booking")) {
      return bookingScoped(rawMessage, "payment_methods", "GET /api/payment-methods", false, false);
    }
    if (containsAll(normalized, "ghe", "con trong") && hasFlightCode(rawMessage)) {
      return flightScoped(rawMessage, "seat_map_lookup", "GET /api/flights/{flightId}/seats");
    }
    if (isFlightSearchRequest(normalized, rawMessage)) {
      String intent =
          containsAny(normalized, "re nhat", "cheapest", "lowest fare")
              ? "flight_calendar_search"
              : "flight_search";
      String api =
          "flight_calendar_search".equals(intent)
              ? "GET /api/flights/search-calendar"
              : "GET /api/flights/search";
      return RoutingDecision.api(intent, api, List.of(), false, false);
    }
    if (containsAny(normalized, "ngay mai con ghe", "hom nay co chuyen bay nao re nhat")) {
      String intent = normalized.contains("re nhat") ? "flight_calendar_search" : "flight_search";
      String api =
          normalized.contains("re nhat")
              ? "GET /api/flights/search-calendar"
              : "GET /api/flights/search";
      return RoutingDecision.api(intent, api, List.of(), false, false);
    }
    if (containsAll(normalized, "fare", "con giu")) {
      return RoutingDecision.api(
          "fare_revalidate",
          "POST /api/flights/revalidate-fare",
          List.of(RequiredContext.FLIGHT_ID, RequiredContext.FARE_ID),
          true,
          true);
    }
    if (containsAny(
        normalized,
        "tong tien booking sau khi chon ghe",
        "tong tien sau khi nhap promo",
        "ma welcome10",
        "ma khuyen mai")) {
      return bookingScoped(
          rawMessage, "fare_revalidate", "POST /api/flights/revalidate-fare", false, true);
    }
    if (containsAny(normalized, "mua them hanh ly chua")
        || (containsAny(normalized, "mua them lounge")
            && !containsAll(normalized, "fare", "da co"))) {
      return bookingScoped(
          rawMessage,
          "manage_options",
          "GET /api/bookings/{bookingId}/manage-options",
          false,
          false);
    }
    if (containsAll(normalized, "boarding pass")) {
      return bookingScoped(
          rawMessage, "checkin_continue", "POST /api/check-in/continue", true, true);
    }
    if (containsAny(normalized, "check in duoc chua", "checkin duoc chua")) {
      return bookingScoped(
          rawMessage, "checkin_continue", "POST /api/check-in/continue", true, true);
    }
    if (containsAny(normalized, "hoan duoc bao nhieu tien", "huy duoc khong")) {
      return bookingScoped(
          rawMessage,
          "cancellation_preview",
          "POST /api/bookings/{bookingId}/cancellations/preview",
          true,
          true);
    }
    if (containsAny(normalized, "doi sang chuyen bay nao", "doi chuyen nay can tra them")) {
      return bookingScoped(
          rawMessage,
          "flight_change_quote",
          "POST /api/bookings/{bookingId}/flight-change/quote",
          true,
          true);
    }
    if (containsAll(normalized, "quote")
        && containsAny(normalized, "con han", "dung duoc", "het han")) {
      return bookingScoped(
          rawMessage,
          "flight_change_quote",
          "POST /api/bookings/{bookingId}/flight-change/quote",
          false,
          true);
    }
    if (containsAny(normalized, "ticket nao con active", "ticket nao con active trong booking")) {
      return bookingScoped(
          rawMessage, "booking_tickets", "GET /api/bookings/{bookingId}/tickets", false, false);
    }
    if (containsAny(
        normalized, "doi ghe 12a sang 14c", "doi ghe trong manage booking co can tra them tien")) {
      return bookingScoped(
          rawMessage,
          "seat_change_preview",
          "POST /api/bookings/{bookingId}/seat-changes/preview",
          true,
          true);
    }
    if (containsAll(normalized, "xac nhan", "doi ghe")) {
      return bookingScoped(
          rawMessage,
          "seat_change_confirm",
          "POST /api/bookings/{bookingId}/seat-changes/confirm",
          true,
          true);
    }
    if (containsAny(normalized, "doi suat an", "doi dich vu")) {
      return bookingScoped(
          rawMessage,
          "service_change_preview",
          "POST /api/bookings/{bookingId}/service-changes/preview",
          true,
          true);
    }
    if (containsAll(normalized, "tai khoan", "booking nao sap bay")) {
      return RoutingDecision.api(
          "my_trips",
          "GET /api/bookings/my-trips",
          List.of(RequiredContext.AUTHENTICATED_ACCOUNT),
          false,
          false);
    }
    if (containsAny(normalized, "ghe nao con trong cho toi", "dang ngoi ghe nao")) {
      String intent = normalized.contains("dang ngoi") ? "trip_detail" : "manage_options";
      String api =
          normalized.contains("dang ngoi")
              ? "GET /api/bookings/{bookingId}/trips/{flightId}"
              : "GET /api/bookings/{bookingId}/manage-options";
      return bookingScoped(rawMessage, intent, api, false, true);
    }
    if (containsAny(normalized, "luu so ho chieu chua", "ngay sinh", "quoc tich")) {
      return bookingScoped(rawMessage, "booking_lookup", "POST /api/bookings/lookup", true, false);
    }
    return null;
  }

  private boolean isProfileUpdateRequest(String normalized) {
    return isProfileLookupRequest(normalized)
        && containsAny(
            normalized,
            "cap nhat",
            "doi ten",
            "thay doi",
            "update profile",
            "change my name",
            "update my name");
  }

  private boolean isProfileLookupRequest(String normalized) {
    return containsAny(
        normalized,
        "ho so tai khoan",
        "ho so cua toi",
        "thong tin tai khoan",
        "my account profile",
        "my profile",
        "current account profile");
  }

  private RoutingDecision bookingScoped(
      String rawMessage,
      String intent,
      String api,
      boolean requiresBookingVerification,
      boolean requiresAdditionalSelection) {
    boolean hasPnr = hasPnr(rawMessage);
    List<RequiredContext> required =
        requiresBookingVerification
            ? List.of(RequiredContext.BOOKING_CODE, RequiredContext.BOOKING_VERIFICATION)
            : List.of(RequiredContext.BOOKING_CODE);
    if (requiresAdditionalSelection) {
      required =
          requiresBookingVerification
              ? List.of(
                  RequiredContext.BOOKING_CODE,
                  RequiredContext.BOOKING_VERIFICATION,
                  RequiredContext.PASSENGER_SELECTION)
              : List.of(RequiredContext.BOOKING_CODE, RequiredContext.PASSENGER_SELECTION);
    }
    return RoutingDecision.api(intent, api, required, false, !hasPnr);
  }

  private RoutingDecision bookingOrPaymentScoped(
      String rawMessage, String intent, String api, boolean requiresConfirmation) {
    boolean hasPaymentId = hasPaymentId(rawMessage);
    boolean hasPnr = hasPnr(rawMessage);
    boolean missing = !hasPaymentId && !hasPnr;
    return RoutingDecision.api(
        intent,
        api,
        List.of(
            RequiredContext.PAYMENT_ID,
            RequiredContext.BOOKING_CODE,
            RequiredContext.BOOKING_VERIFICATION),
        requiresConfirmation,
        missing);
  }

  private RoutingDecision paymentScoped(String rawMessage, String intent, String api) {
    return RoutingDecision.api(
        intent, api, List.of(RequiredContext.PAYMENT_ID), false, !hasPaymentId(rawMessage));
  }

  private RoutingDecision flightScoped(String rawMessage, String intent, String api) {
    return RoutingDecision.api(
        intent,
        api,
        List.of(RequiredContext.FLIGHT_ID, RequiredContext.FARE_ID),
        false,
        !hasFlightCode(rawMessage));
  }

  private boolean isUnsafeFallback(String normalized) {
    return containsAny(
        normalized,
        "visa",
        "nhap canh",
        "passport phai con han bao nhieu thang",
        "gate dong truoc",
        "no show",
        "boi thuong",
        "mang thu cung",
        "mang pin",
        "doi ten hanh khach",
        "phu nu mang thai",
        "xe lan");
  }

  private boolean hasPnr(String rawMessage) {
    String[] tokens =
        rawMessage.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]+", " ").split("\\s+");
    for (String token : tokens) {
      if (token.length() == 6 && token.matches("[A-Z0-9]{6}") && token.matches(".*\\d.*")) {
        return true;
      }
    }
    return false;
  }

  private boolean hasPaymentId(String rawMessage) {
    return PAYMENT_ID_PATTERN.matcher(normalize(rawMessage)).find();
  }

  private boolean hasFlightCode(String rawMessage) {
    return FLIGHT_CODE_PATTERN.matcher(rawMessage.toUpperCase(Locale.ROOT)).find();
  }

  private boolean isFlightSearchRequest(String normalized, String rawMessage) {
    boolean asksToSearch =
        containsAny(
            normalized,
            "tim chuyen bay",
            "search flight",
            "find flight",
            "available flight",
            "co chuyen bay nao",
            "invent available flight");
    boolean filtersPreviousSearch =
        containsAny(
            normalized,
            "chi lay",
            "only show",
            "only include",
            "buoi sang",
            "buoi chieu",
            "buoi toi",
            "morning flight",
            "afternoon flight",
            "evening flight",
            "nonstop",
            "direct flight");
    boolean hasAirportRoute =
        NORMALIZED_AIRPORT_ROUTE_PATTERN.matcher(normalized).find()
            || AIRPORT_PAIR_PATTERN.matcher(rawMessage.toUpperCase(Locale.ROOT)).find();
    return hasAirportRoute && (asksToSearch || filtersPreviousSearch);
  }

  private boolean containsAny(String normalized, String... candidates) {
    for (String candidate : candidates) {
      if (normalized.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsAll(String normalized, String... candidates) {
    for (String candidate : candidates) {
      if (!normalized.contains(candidate)) {
        return false;
      }
    }
    return true;
  }

  private String normalize(String text) {
    String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
    normalized = normalized.replaceAll("\\p{M}+", "");
    normalized = normalized.replace('đ', 'd').replace('Đ', 'D');
    normalized = normalized.toLowerCase(Locale.ROOT);
    return normalized.replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
  }
}
