package com.example.modulebooking.application;

public record SeatChangeCommand(Long passengerId, Long fromSeatId, Long toSeatId) {}
