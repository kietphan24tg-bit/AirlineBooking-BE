package com.example.appbootstrap.controller.booking.dto;

import com.example.appbootstrap.application.booking.AncillaryCatalogService;
import java.math.BigDecimal;
import java.util.List;

public record AncillaryCatalogResponse(
    String currency, List<AncillaryServiceGroupResponse> services) {

  public static AncillaryCatalogResponse from(AncillaryCatalogService.AncillaryCatalogView view) {
    return new AncillaryCatalogResponse(
        view.currency(),
        view.services().stream().map(AncillaryServiceGroupResponse::from).toList());
  }

  public record AncillaryServiceGroupResponse(
      Long serviceId,
      String serviceType,
      String serviceName,
      String iconUrl,
      String selectionMode,
      List<AncillaryOptionResponse> options) {
    static AncillaryServiceGroupResponse from(
        AncillaryCatalogService.AncillaryServiceGroupView view) {
      return new AncillaryServiceGroupResponse(
          view.serviceId(),
          view.serviceType(),
          view.serviceName(),
          view.iconUrl(),
          view.selectionMode(),
          view.options().stream().map(AncillaryOptionResponse::from).toList());
    }
  }

  public record AncillaryOptionResponse(
      Long optionId,
      String name,
      String description,
      BigDecimal price,
      String currency,
      String imageUrl,
      boolean isActive) {
    static AncillaryOptionResponse from(AncillaryCatalogService.AncillaryOptionView view) {
      return new AncillaryOptionResponse(
          view.optionId(),
          view.name(),
          view.description(),
          view.price(),
          view.currency(),
          view.imageUrl(),
          view.isActive());
    }
  }
}
