package com.example.moduleweb.controller.flight.dto;

import com.example.moduleweb.application.flight.FlightSeatMapService;
import java.math.BigDecimal;
import java.util.List;

public record FlightSeatMapResponse(
    Long flightId,
    Long fareId,
    Long fareClassId,
    String fareCode,
    String seatSelectionPolicy,
    PolicyDisplayResponse policyDisplay,
    String includedSeatCategoryCode,
    String currency,
    LayoutResponse layout,
    LegendResponse legend,
    List<RowResponse> rows) {

  public static FlightSeatMapResponse from(FlightSeatMapService.FlightSeatMapView view) {
    return new FlightSeatMapResponse(
        view.flightId(),
        view.fareId(),
        view.fareClassId(),
        view.fareCode(),
        view.seatSelectionPolicy(),
        PolicyDisplayResponse.from(view.seatSelectionPolicy()),
        view.includedSeatCategoryCode(),
        view.currency(),
        LayoutResponse.from(view.layout()),
        LegendResponse.from(view.legend()),
        view.rows().stream().map(RowResponse::from).toList());
  }

  public record LayoutResponse(
      List<String> columns,
      List<String> aisleAfterColumns,
      List<SeatClassZoneResponse> seatClassZones) {
    static LayoutResponse from(FlightSeatMapService.AircraftLayoutView view) {
      return new LayoutResponse(
          view.columns(),
          view.aisleAfterColumns(),
          view.seatClassZones().stream().map(SeatClassZoneResponse::from).toList());
    }
  }

  public record SeatClassZoneResponse(String seatClass, Integer rowStart, Integer rowEnd) {
    static SeatClassZoneResponse from(FlightSeatMapService.SeatClassZoneView view) {
      return new SeatClassZoneResponse(view.seatClass(), view.rowStart(), view.rowEnd());
    }
  }

  public record LegendResponse(
      List<String> statuses,
      List<String> seatTypes,
      List<String> seatCategories,
      List<LegendItemResponse> statusItems,
      List<LegendItemResponse> seatTypeItems,
      List<LegendItemResponse> seatCategoryItems,
      List<PricingLegendItemResponse> pricingItems) {
    static LegendResponse from(FlightSeatMapService.LegendView view) {
      return new LegendResponse(
          view.statuses(),
          view.seatTypes(),
          view.seatCategories(),
          view.statuses().stream().map(LegendItemResponse::status).toList(),
          view.seatTypes().stream().map(LegendItemResponse::seatType).toList(),
          view.seatCategories().stream().map(LegendItemResponse::seatCategory).toList(),
          List.of(
              new PricingLegendItemResponse(
                  "FREE", "Free", "Seat can be selected without any extra charge."),
              new PricingLegendItemResponse(
                  "PAID", "Paid", "Seat requires an additional surcharge."),
              new PricingLegendItemResponse(
                  "UNAVAILABLE", "Unavailable", "Seat is blocked, reserved, or already booked."),
              new PricingLegendItemResponse(
                  "NOT_ALLOWED",
                  "Not allowed",
                  "Seat cannot be chosen for this fare or at this stage.")));
    }
  }

  public record RowResponse(Integer rowNumber, String seatClass, List<SeatResponse> cells) {
    static RowResponse from(FlightSeatMapService.RowView view) {
      return new RowResponse(
          view.rowNumber(),
          view.seatClass(),
          view.cells().stream().map(SeatResponse::from).toList());
    }
  }

  public record SeatResponse(
      String columnLetter,
      Long seatId,
      String seatNumber,
      String seatClass,
      String seatType,
      SeatDescriptorResponse seatTypeDisplay,
      String seatCategory,
      SeatDescriptorResponse seatCategoryDisplay,
      String status,
      SeatDescriptorResponse statusDisplay,
      boolean selectable,
      boolean included,
      BigDecimal extraPrice,
      String currency,
      String reason,
      DisplayPriceResponse displayPrice,
      PricingBadgeResponse pricingBadge) {
    static SeatResponse from(FlightSeatMapService.SeatView view) {
      return new SeatResponse(
          view.columnLetter(),
          view.seatId(),
          view.seatNumber(),
          view.seatClass(),
          view.seatType(),
          SeatDescriptorResponse.seatType(view.seatType()),
          view.seatCategory(),
          SeatDescriptorResponse.seatCategory(view.seatCategory()),
          view.status(),
          SeatDescriptorResponse.status(view.status()),
          view.selectable(),
          view.included(),
          view.extraPrice(),
          view.currency(),
          view.reason(),
          DisplayPriceResponse.from(view),
          PricingBadgeResponse.from(view));
    }
  }

  public record PolicyDisplayResponse(String code, String label, String description) {
    static PolicyDisplayResponse from(String code) {
      return switch (normalize(code)) {
        case "INCLUDED" ->
            new PolicyDisplayResponse(
                "INCLUDED",
                "Seat included",
                "Eligible seats can be selected in advance without extra charge.");
        case "PAID" ->
            new PolicyDisplayResponse(
                "PAID",
                "Seat selection with surcharge",
                "Some seats require an additional fee depending on their type or location.");
        case "CHECKIN_ONLY" ->
            new PolicyDisplayResponse(
                "CHECKIN_ONLY",
                "Check-in only",
                "Seats become available for selection during check-in.");
        default ->
            new PolicyDisplayResponse(
                normalize(code),
                "Selection not available",
                "Advance seat selection is not allowed.");
      };
    }
  }

  public record LegendItemResponse(String code, String label, String description) {
    static LegendItemResponse status(String code) {
      return new LegendItemResponse(code, labelForStatus(code), descriptionForStatus(code));
    }

    static LegendItemResponse seatType(String code) {
      return new LegendItemResponse(code, labelForSeatType(code), descriptionForSeatType(code));
    }

    static LegendItemResponse seatCategory(String code) {
      return new LegendItemResponse(
          code, labelForSeatCategory(code), descriptionForSeatCategory(code));
    }
  }

  public record SeatDescriptorResponse(String code, String label, String description) {
    static SeatDescriptorResponse status(String code) {
      return new SeatDescriptorResponse(code, labelForStatus(code), descriptionForStatus(code));
    }

    static SeatDescriptorResponse seatType(String code) {
      return new SeatDescriptorResponse(code, labelForSeatType(code), descriptionForSeatType(code));
    }

    static SeatDescriptorResponse seatCategory(String code) {
      return new SeatDescriptorResponse(
          code, labelForSeatCategory(code), descriptionForSeatCategory(code));
    }
  }

  public record PricingLegendItemResponse(String code, String label, String description) {}

  public record DisplayPriceResponse(
      String type, String text, BigDecimal amount, String currency, String description) {
    static DisplayPriceResponse from(FlightSeatMapService.SeatView view) {
      PricingBadgeResponse badge = PricingBadgeResponse.from(view);
      if ("FREE".equals(badge.code())) {
        return new DisplayPriceResponse(
            "FREE",
            view.included() ? "Included" : "Free",
            BigDecimal.ZERO,
            view.currency(),
            view.included()
                ? "This seat is covered by the selected fare."
                : "This seat has no additional surcharge.");
      }
      if ("PAID".equals(badge.code()) && view.extraPrice() != null) {
        return new DisplayPriceResponse(
            "PAID",
            formatPaidText(view.extraPrice(), view.currency()),
            view.extraPrice(),
            view.currency(),
            "Additional surcharge applied for this seat.");
      }
      return new DisplayPriceResponse(
          badge.code(), badge.label(), view.extraPrice(), view.currency(), badge.description());
    }
  }

  public record PricingBadgeResponse(String code, String label, String description) {
    static PricingBadgeResponse from(FlightSeatMapService.SeatView view) {
      if (view.selectable() && view.extraPrice() != null && view.extraPrice().signum() == 0) {
        return new PricingBadgeResponse(
            "FREE", "Free", "This seat does not add any surcharge for the selected fare.");
      }
      if (view.selectable() && view.extraPrice() != null && view.extraPrice().signum() > 0) {
        return new PricingBadgeResponse("PAID", "Paid", "This seat requires an extra surcharge.");
      }
      if ("SEAT_UNAVAILABLE".equals(normalize(view.reason()))) {
        return new PricingBadgeResponse(
            "UNAVAILABLE", "Unavailable", "This seat cannot be selected.");
      }
      return new PricingBadgeResponse(
          "NOT_ALLOWED", "Not allowed", "This seat is not eligible for the selected fare.");
    }
  }

  private static String labelForStatus(String code) {
    return switch (normalize(code)) {
      case "AVAILABLE" -> "Available";
      case "RESERVED" -> "Reserved";
      case "BOOKED" -> "Booked";
      case "UNAVAILABLE" -> "Unavailable";
      default -> fallbackLabel(code);
    };
  }

  private static String descriptionForStatus(String code) {
    return switch (normalize(code)) {
      case "AVAILABLE" -> "Seat is open for selection if your fare allows it.";
      case "RESERVED" -> "Seat is being held temporarily and cannot be picked.";
      case "BOOKED" -> "Seat has already been assigned to another passenger.";
      case "UNAVAILABLE" -> "Seat is blocked from sale or temporarily disabled.";
      default -> fallbackDescription(code);
    };
  }

  private static String labelForSeatType(String code) {
    return switch (normalize(code)) {
      case "WINDOW" -> "Window";
      case "MIDDLE" -> "Middle";
      case "AISLE" -> "Aisle";
      default -> fallbackLabel(code);
    };
  }

  private static String descriptionForSeatType(String code) {
    return switch (normalize(code)) {
      case "WINDOW" -> "Seat next to the aircraft window.";
      case "MIDDLE" -> "Center seat within a seat block.";
      case "AISLE" -> "Seat with direct access to the aisle.";
      default -> fallbackDescription(code);
    };
  }

  private static String labelForSeatCategory(String code) {
    return switch (normalize(code)) {
      case "STANDARD" -> "Standard";
      case "PREFERRED" -> "Preferred";
      case "EXIT_ROW" -> "Exit row";
      case "EXTRA_LEGROOM" -> "Extra legroom";
      default -> fallbackLabel(code);
    };
  }

  private static String descriptionForSeatCategory(String code) {
    return switch (normalize(code)) {
      case "STANDARD" -> "Regular seat with no special location benefits.";
      case "PREFERRED" -> "More desirable seat location, often closer to the front.";
      case "EXIT_ROW" -> "Seat located at an exit row, usually with more space.";
      case "EXTRA_LEGROOM" -> "Seat with additional legroom compared with standard seats.";
      default -> fallbackDescription(code);
    };
  }

  private static String normalize(String code) {
    return code == null ? "" : code.trim().toUpperCase();
  }

  private static String fallbackLabel(String code) {
    if (code == null || code.isBlank()) {
      return "";
    }
    return code.trim().replace('_', ' ');
  }

  private static String fallbackDescription(String code) {
    if (code == null || code.isBlank()) {
      return "";
    }
    return "Mapped from code: " + normalize(code);
  }

  private static String formatPaidText(BigDecimal amount, String currency) {
    if (amount == null) {
      return "Paid";
    }
    BigDecimal normalizedAmount = amount.stripTrailingZeros();
    String amountText =
        normalizedAmount.scale() <= 0
            ? normalizedAmount.toPlainString()
            : normalizedAmount.toPlainString();
    return currency == null || currency.isBlank()
        ? "+" + amountText
        : "+" + amountText + " " + currency;
  }
}
