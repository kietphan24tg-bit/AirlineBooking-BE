package com.example.modulebooking.application;

public record PassengerSummaryCommand(Integer adult, Integer child, Integer infant) {

  public int seatedPassengers() {
    return adult + child;
  }

  public int totalPassengers() {
    return adult + child + infant;
  }
}
