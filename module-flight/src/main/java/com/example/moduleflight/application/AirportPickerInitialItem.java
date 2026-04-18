package com.example.moduleflight.application;

public record AirportPickerInitialItem(
    long id,
    String code,
    String name,
    String city,
    String country,
    String countryCode,
    String source) {}
