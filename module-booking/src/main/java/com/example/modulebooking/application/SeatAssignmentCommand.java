package com.example.modulebooking.application;

import java.util.List;

public record SeatAssignmentCommand(List<AssignmentCommand> assignments) {

  public record AssignmentCommand(Long passengerId, Long seatId) {}
}
