package com.example.moduleflight.application;

import com.example.moduleflight.application.FlightPaginationService.PageResult;
import com.example.moduleflight.application.PromotionBatchPreviewService.PromoPreview;
import com.example.moduleflight.application.PromotionBatchPreviewService.PromotionContext;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightSearchFacadeService {

  private final FlightQueryService flightQueryService;
  private final FlightPricingService flightPricingService;
  private final PromotionBatchPreviewService promotionBatchPreviewService;
  private final FlightRankingService flightRankingService;
  private final FlightPaginationService flightPaginationService;
  private final CountryCurrencyResolver countryCurrencyResolver;

  public FlightSearchFacadeService(
      FlightQueryService flightQueryService,
      FlightPricingService flightPricingService,
      PromotionBatchPreviewService promotionBatchPreviewService,
      FlightRankingService flightRankingService,
      FlightPaginationService flightPaginationService,
      CountryCurrencyResolver countryCurrencyResolver) {
    this.flightQueryService = flightQueryService;
    this.flightPricingService = flightPricingService;
    this.promotionBatchPreviewService = promotionBatchPreviewService;
    this.flightRankingService = flightRankingService;
    this.flightPaginationService = flightPaginationService;
    this.countryCurrencyResolver = countryCurrencyResolver;
  }

  @Transactional(readOnly = true)
  public FlightSearchResult search(FlightSearchQuery query) {
    PromotionContext promotionContext = promotionBatchPreviewService.loadContext(query.promoCode());

    String outboundCursor =
        StringUtils.hasText(query.outboundCursor()) ? query.outboundCursor() : query.cursor();
    DirectionResult outbound =
        searchDirection(
            query.fromAirportCode(),
            query.toAirportCode(),
            query.departureDate(),
            query,
            outboundCursor,
            promotionContext);

    DirectionResult inbound = DirectionResult.empty(query.limit());
    if (query.tripType() == FlightSearchTripType.ROUND_TRIP && query.returnDate() != null) {
      inbound =
          searchDirection(
              query.toAirportCode(),
              query.fromAirportCode(),
              query.returnDate(),
              query,
              query.inboundCursor(),
              promotionContext);
    }

    return new FlightSearchResult(
        outbound.items(), inbound.items(), outbound.pageInfo(), inbound.pageInfo());
  }

  private DirectionResult searchDirection(
      String fromCode,
      String toCode,
      java.time.LocalDate date,
      FlightSearchQuery query,
      String cursor,
      PromotionContext promotionContext) {
    List<List<FlightFareEntity>> groupedFlights =
        flightQueryService.findByRouteAndDate(fromCode, toCode, date);
    List<FlightSearchPageItem> rankedOptions = new ArrayList<>();

    for (List<FlightFareEntity> fares : groupedFlights) {
      if (fares == null || fares.isEmpty()) {
        continue;
      }
      FlightFareEntity firstFare = fares.getFirst();
      String countryCode = countryCurrencyResolver.resolveCountryCode(firstFare);
      String currency = countryCurrencyResolver.resolveCurrency(countryCode);
      List<RankedFareOption> rankedFareOptions = new ArrayList<>();

      for (FlightFareEntity fare : fares) {
        BigDecimal originalTotalPrice =
            flightPricingService.calculateTotalPrice(
                fare.getTotalPrice(), query.adult(), query.child(), query.infant());
        boolean fareAvailable = "AVAILABLE".equalsIgnoreCase(fare.getStatus());
        PromoPreview preview =
            promotionBatchPreviewService.preview(
                promotionContext, originalTotalPrice, currency, countryCode, fareAvailable);

        FlightSearchResult.FareOptionView fareOption =
            new FlightSearchResult.FareOptionView(
                fare.getId(),
                fare.getFareClass().getCode(),
                fare.getFareClass().getCabinClass(),
                normalizeFareStatus(fare.getStatus()),
                originalTotalPrice,
                "APPLIED".equals(preview.status()) ? preview.discountedPrice() : null,
                "APPLIED".equals(preview.status()) ? preview.discountAmount() : BigDecimal.ZERO,
                "APPLIED".equals(preview.status()) ? preview.discountPercent() : null,
                preview.status(),
                preview.message());
        rankedFareOptions.add(
            new RankedFareOption(
                fareOption,
                flightRankingService.buildFareRankKey(fare, preview, originalTotalPrice)));
      }

      rankedFareOptions.sort(Comparator.comparing(RankedFareOption::rankKey));
      if (rankedFareOptions.isEmpty()) {
        continue;
      }

      FlightSearchResult.FlightOptionView flightOption =
          new FlightSearchResult.FlightOptionView(
              firstFare.getFlight().getId(),
              firstFare.getFlight().getFlightNumber(),
              firstFare.getFlight().getAirline().getCode(),
              firstFare.getFlight().getAirline().getName(),
              firstFare.getFlight().getDepartureAirport().getCode(),
              firstFare.getFlight().getArrivalAirport().getCode(),
              firstFare.getFlight().getFlightDate(),
              firstFare.getFlight().getDepartureTime(),
              firstFare.getFlight().getArrivalTime(),
              currency,
              rankedFareOptions.stream().map(RankedFareOption::view).toList());
      rankedOptions.add(new RankedFlightRow(flightOption, rankedFareOptions.getFirst().rankKey()));
    }

    PageResult<? extends FlightSearchPageItem> page =
        flightPaginationService.paginate(rankedOptions, query.limit(), cursor);
    List<FlightSearchResult.FlightOptionView> items =
        page.items().stream().map(FlightSearchPageItem::view).toList();
    return new DirectionResult(
        items, new FlightSearchResult.PageInfo(page.nextCursor(), page.hasMore(), page.limit()));
  }

  private String normalizeFareStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return "SOLD_OUT";
    }
    return status.trim().toUpperCase();
  }

  private record RankedFareOption(FlightSearchResult.FareOptionView view, FlightRankKey rankKey) {}

  private record RankedFlightRow(FlightSearchResult.FlightOptionView view, FlightRankKey rankKey)
      implements FlightSearchPageItem {}

  private record DirectionResult(
      List<FlightSearchResult.FlightOptionView> items, FlightSearchResult.PageInfo pageInfo) {
    private static DirectionResult empty(int limit) {
      return new DirectionResult(List.of(), new FlightSearchResult.PageInfo(null, false, limit));
    }
  }
}
