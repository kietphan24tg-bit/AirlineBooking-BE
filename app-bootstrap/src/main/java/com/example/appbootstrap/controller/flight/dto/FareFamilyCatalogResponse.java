package com.example.appbootstrap.controller.flight.dto;

import com.example.moduleflight.application.FareFamilyCatalogService;
import java.math.BigDecimal;
import java.util.List;

public record FareFamilyCatalogResponse(List<FareFamilyItemResponse> data) {

  public static FareFamilyCatalogResponse from(
      List<FareFamilyCatalogService.FareFamilyView> fareFamilies) {
    return new FareFamilyCatalogResponse(
        fareFamilies.stream().map(FareFamilyItemResponse::from).toList());
  }

  public record FareFamilyItemResponse(
      Long fareClassId,
      String fareClassCode,
      String fareClassName,
      String cabinClass,
      Integer baggageCheckinKg,
      Integer baggageHandKg,
      BigDecimal changeFee,
      BigDecimal refundFee,
      boolean refundable,
      boolean changeable,
      boolean priorityCheckin,
      boolean priorityBoarding,
      BigDecimal mileageRate,
      boolean loungeAccess,
      boolean seatSelection,
      String seatSelectionPolicy,
      String mealType,
      boolean wifiIncluded,
      boolean fastTrackSecurity,
      String includedSeatCategoryCode,
      ReferencePriceResponse referencePrice) {

    public static FareFamilyItemResponse from(FareFamilyCatalogService.FareFamilyView view) {
      return new FareFamilyItemResponse(
          view.fareClassId(),
          view.fareClassCode(),
          view.fareClassName(),
          view.cabinClass(),
          view.baggageCheckinKg(),
          view.baggageHandKg(),
          view.changeFee(),
          view.refundFee(),
          view.refundable(),
          view.changeable(),
          view.priorityCheckin(),
          view.priorityBoarding(),
          view.mileageRate(),
          view.loungeAccess(),
          view.seatSelection(),
          view.seatSelectionPolicy(),
          view.mealType(),
          view.wifiIncluded(),
          view.fastTrackSecurity(),
          view.includedSeatCategoryCode(),
          ReferencePriceResponse.from(view.referencePrice()));
    }
  }

  public record ReferencePriceResponse(String countryCode, String currency, BigDecimal amount) {
    static ReferencePriceResponse from(FareFamilyCatalogService.ReferencePriceView view) {
      if (view == null) {
        return null;
      }
      return new ReferencePriceResponse(view.countryCode(), view.currency(), view.amount());
    }
  }
}
