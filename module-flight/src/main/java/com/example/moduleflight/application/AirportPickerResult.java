package com.example.moduleflight.application;

import java.util.List;

public record AirportPickerResult(
    String market,
    List<AirportPickerItem> recent,
    List<AirportPickerItem> suggested,
    List<AirportPickerInitialItem> initialItems) {}
