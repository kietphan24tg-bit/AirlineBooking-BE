package com.example.appbootstrap.controller.flight.dto;

import com.example.moduleflight.application.AirportPickerInitialItem;
import com.example.moduleflight.application.AirportPickerItem;
import com.example.moduleflight.application.AirportPickerResult;
import java.util.List;

public record AirportPickerResponse(
    String market,
    List<AirportPickerItemResponse> recent,
    List<AirportPickerItemResponse> suggested,
    List<AirportPickerInitialItemResponse> initialItems) {

  public static AirportPickerResponse from(AirportPickerResult result) {
    return new AirportPickerResponse(
        result.market(),
        result.recent().stream().map(AirportPickerItemResponse::from).toList(),
        result.suggested().stream().map(AirportPickerItemResponse::from).toList(),
        result.initialItems().stream().map(AirportPickerInitialItemResponse::from).toList());
  }

  public record AirportPickerItemResponse(
      long id, String code, String name, String city, String country, String countryCode) {

    public static AirportPickerItemResponse from(AirportPickerItem item) {
      return new AirportPickerItemResponse(
          item.id(), item.code(), item.name(), item.city(), item.country(), item.countryCode());
    }
  }

  public record AirportPickerInitialItemResponse(
      long id,
      String code,
      String name,
      String city,
      String country,
      String countryCode,
      String source) {

    static AirportPickerInitialItemResponse from(AirportPickerInitialItem item) {
      return new AirportPickerInitialItemResponse(
          item.id(),
          item.code(),
          item.name(),
          item.city(),
          item.country(),
          item.countryCode(),
          item.source());
    }
  }
}
