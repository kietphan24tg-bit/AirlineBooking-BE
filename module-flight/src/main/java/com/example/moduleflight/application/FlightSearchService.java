package com.example.moduleflight.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightSearchService {

  private final FlightSearchFacadeService facadeService;

  public FlightSearchService(FlightSearchFacadeService facadeService) {
    this.facadeService = facadeService;
  }

  @Transactional(readOnly = true)
  public FlightSearchResult search(FlightSearchQuery query) {
    return facadeService.search(query);
  }
}
