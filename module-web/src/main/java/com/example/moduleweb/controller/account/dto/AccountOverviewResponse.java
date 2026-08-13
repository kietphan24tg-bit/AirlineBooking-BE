package com.example.moduleweb.controller.account.dto;

import com.example.moduleweb.application.account.AccountOverviewService;
import com.example.moduleweb.application.booking.UserTripHistoryService;
import com.example.moduleweb.controller.flight.dto.AirportPickerResponse;
import com.example.moduleweb.controller.identity.dto.CurrentUserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    name = "AccountOverviewResponse",
    description = "Aggregated overview data for the authenticated account page")
public record AccountOverviewResponse(
    CurrentUserResponse profile,
    TripStatsResponse tripStats,
    List<AirportPickerResponse.AirportPickerItemResponse> recentAirports) {

  public static AccountOverviewResponse from(AccountOverviewService.AccountOverviewView view) {
    return new AccountOverviewResponse(
        CurrentUserResponse.from(view.user()),
        TripStatsResponse.from(view.tripStats()),
        view.recentAirports().stream()
            .map(AirportPickerResponse.AirportPickerItemResponse::from)
            .toList());
  }

  public record TripStatsResponse(
      @Schema(example = "12") int completed,
      @Schema(example = "2") int upcoming,
      @Schema(example = "1") int cancelled) {

    static TripStatsResponse from(UserTripHistoryService.TripStatusCounts counts) {
      return new TripStatsResponse(counts.completed(), counts.upcoming(), counts.cancelled());
    }
  }
}
