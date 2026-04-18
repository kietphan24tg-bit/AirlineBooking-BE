package com.example.moduleflight.application;

public record AirportMarketContext(
    Long userId, String acceptLanguage, String timezone, String siteLocale, String geoIpCountry) {}
