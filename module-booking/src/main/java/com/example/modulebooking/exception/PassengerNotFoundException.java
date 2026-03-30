package com.example.modulebooking.exception;

import com.example.common.exception.AppException;
import java.util.Map;

public class PassengerNotFoundException extends AppException {

  public PassengerNotFoundException(Long passengerId) {
    super(
        "PASSENGER_NOT_FOUND",
        "Passenger not found",
        404,
        Map.of("passengerId", passengerId));
  }
}
