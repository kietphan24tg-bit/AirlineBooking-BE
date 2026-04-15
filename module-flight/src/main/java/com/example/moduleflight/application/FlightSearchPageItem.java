package com.example.moduleflight.application;

/** Ranked flight row passed into {@link FlightPaginationService#paginate} from flight search. */
public interface FlightSearchPageItem extends FlightPaginationService.HasRankKey {

  FlightSearchResult.FlightOptionView view();
}
