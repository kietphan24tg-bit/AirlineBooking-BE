package com.example.moduleflight.application;

import com.example.moduleflight.application.FlightPaginationService.PageResult;
import com.example.moduleflight.application.PromotionBatchPreviewService.PromoPreview;
import com.example.moduleflight.application.PromotionBatchPreviewService.PromotionContext;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightSearchFacadeService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightSearchFacadeService.class);

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

    List<FlightSearchResult.FlightOptionView> outboundItems = outbound.items();
    List<FlightSearchResult.FlightOptionView> inboundItems = inbound.items();
    return new FlightSearchResult(
        outboundItems,
        inboundItems,
        outbound.pageInfo(),
        inbound.pageInfo(),
        FlightSearchResult.priceRangeFromFlights(outboundItems),
        FlightSearchResult.priceRangeFromFlights(inboundItems));
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
        if (fare.getTotalPrice() == null) {
          String fareCode = fare.getFareClass() != null ? fare.getFareClass().getCode() : "unknown";
          LOG.warn("Skipping fare id={} fareCode={}: total price is null", fare.getId(), fareCode);
          continue;
        }
        BigDecimal originalTotalPrice;
        try {
          originalTotalPrice =
              flightPricingService.calculateTotalPrice(
                  fare.getTotalPrice(), query.adult(), query.child(), query.infant());
        } catch (IllegalArgumentException | NullPointerException ex) {
          String fareCode = fare.getFareClass() != null ? fare.getFareClass().getCode() : "unknown";
          LOG.warn(
              "Skipping fare id={} fareCode={} due to pricing calculation failure",
              fare.getId(),
              fareCode,
              ex);
          continue;
        }
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

      rankedFareOptions = applyFareFilters(rankedFareOptions, query);
      if (rankedFareOptions.isEmpty()) {
        continue;
      }
      FlightEntity flightEntity = firstFare.getFlight();
      if (!passesTimeBuckets(flightEntity, query)) {
        continue;
      }
      if (!passesConnectionFilter(flightEntity, query)) {
        continue;
      }

      rankedFareOptions.sort(Comparator.comparing(RankedFareOption::rankKey));

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

  private List<RankedFareOption> applyFareFilters(
      List<RankedFareOption> rankedFareOptions, FlightSearchQuery query) {
    List<RankedFareOption> out = new ArrayList<>(rankedFareOptions);
    if (!query.cabinClasses().isEmpty()) {
      out.removeIf(
          o -> {
            String cabin = o.view().cabinClass();
            try {
              return !query.cabinClasses().contains(CabinClass.valueOf(cabin));
            } catch (IllegalArgumentException ex) {
              return true;
            }
          });
    }
    if (query.maxBudget() != null) {
      BigDecimal max = query.maxBudget();
      out.removeIf(o -> effectiveFarePrice(o.view()).compareTo(max) > 0);
    }
    return out;
  }

  private static BigDecimal effectiveFarePrice(FlightSearchResult.FareOptionView fare) {
    if (fare.discountedTotalPrice() != null) {
      return fare.discountedTotalPrice();
    }
    return fare.originalTotalPrice();
  }

  private boolean passesTimeBuckets(FlightEntity flight, FlightSearchQuery query) {
    if (query.departureTimeBucket() != null
        && !query.departureTimeBucket().matches(flight.getDepartureTime().toLocalTime())) {
      return false;
    }
    if (query.arrivalTimeBucket() != null
        && !query.arrivalTimeBucket().matches(flight.getArrivalTime().toLocalTime())) {
      return false;
    }
    return true;
  }

  private boolean passesConnectionFilter(FlightEntity flight, FlightSearchQuery query) {
    if (query.connectionFilter() == FlightConnectionFilter.ANY) {
      return true;
    }
    return isDirectFlight(flight);
  }

  /** Current data model only includes single-leg flights; multi-segment search can narrow this. */
  private boolean isDirectFlight(FlightEntity flight) {
    return flight != null;
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
