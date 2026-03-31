package com.example.moduleflight.exception;

import com.example.common.exception.AppException;
import java.util.Map;

public class FlightNotFoundException extends AppException {

  public FlightNotFoundException(Long flightId) {
    super("FLIGHT_NOT_FOUND", "Flight not found", 404, Map.of("flightId", flightId));
  }
}
