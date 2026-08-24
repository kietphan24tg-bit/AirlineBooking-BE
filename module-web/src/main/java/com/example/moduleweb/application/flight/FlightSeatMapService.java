package com.example.moduleweb.application.flight;

import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AircraftLayoutZoneRepository;
import com.example.moduleflight.repository.FlightFareRepository;

public class FlightSeatMapService
    extends com.example.modulebooking.application.FlightSeatMapService {

  public FlightSeatMapService(
      FlightFareRepository flightFareRepository,
      FlightSeatRepository flightSeatRepository,
      AircraftLayoutRepository aircraftLayoutRepository,
      AircraftLayoutZoneRepository aircraftLayoutZoneRepository,
      CountryCurrencyResolver countryCurrencyResolver) {
    super(
        flightFareRepository,
        flightSeatRepository,
        aircraftLayoutRepository,
        aircraftLayoutZoneRepository,
        countryCurrencyResolver);
  }
}
