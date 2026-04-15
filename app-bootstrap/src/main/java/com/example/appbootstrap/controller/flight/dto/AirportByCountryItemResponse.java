package com.example.appbootstrap.controller.flight.dto;

import com.example.moduleflight.application.AirportGroupedItem;

public record AirportByCountryItemResponse(long id, String code, String name, String city) {

  public static AirportByCountryItemResponse from(AirportGroupedItem item) {
    return new AirportByCountryItemResponse(item.id(), item.code(), item.name(), item.city());
  }
}
