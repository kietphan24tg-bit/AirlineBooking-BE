package com.example.modulecheckin.application;

import java.util.List;

public record CheckInContinueCommand(
    String pnrCode, String lastName, List<SegmentSelection> segments) {

  public record SegmentSelection(Long flightId, List<Long> passengerIds) {}
}
