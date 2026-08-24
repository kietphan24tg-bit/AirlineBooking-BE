package com.example.modulebooking.application;

import java.util.List;

public record PassengerServiceSelectionCommand(List<PassengerServicesCommand> passengers) {

  public record PassengerServicesCommand(
      Long passengerId, List<ServiceSelectionCommand> selections) {}

  public record ServiceSelectionCommand(Long optionId, Integer quantity) {}
}
