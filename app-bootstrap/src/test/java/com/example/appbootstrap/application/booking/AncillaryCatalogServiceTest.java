package com.example.moduleweb.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.repository.ServiceOptionRepository;
import com.example.modulebooking.repository.ServiceRepository;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FlightFareRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AncillaryCatalogServiceTest {

  @Mock private ServiceRepository serviceRepository;
  @Mock private ServiceOptionRepository serviceOptionRepository;
  @Mock private FlightFareRepository flightFareRepository;
  @Mock private CountryCurrencyResolver countryCurrencyResolver;

  @Test
  void getCatalog_returnsActiveGroupedOptionsWithStableOrdering() {
    AncillaryCatalogService service =
        new AncillaryCatalogService(
            serviceRepository,
            serviceOptionRepository,
            flightFareRepository,
            countryCurrencyResolver);
    ServiceEntity baggage = serviceEntity(1L, "BAGGAGE", "Baggage Add-on", "bag.png", true);
    ServiceEntity meal = serviceEntity(2L, "MEAL", "Pre-order Meals", "meal.png", true);

    when(serviceRepository.findAllByIsActiveTrueOrderByTypeAscNameAsc())
        .thenReturn(List.of(baggage, meal));
    when(serviceOptionRepository.findActiveByServiceIds(anyCollection()))
        .thenReturn(
            List.of(
                optionEntity(11L, baggage, "Extra 15kg", "Checked bag 15kg", "180000", true),
                optionEntity(12L, baggage, "Extra 20kg", "Checked bag 20kg", "250000", true),
                optionEntity(21L, meal, "Kids Pasta Set", "Child meal", "85000", true),
                optionEntity(22L, meal, "Premium Combo Meal", "Premium meal", "150000", true)));

    AncillaryCatalogService.AncillaryCatalogView view = service.getCatalog();

    assertEquals("VND", view.currency());
    assertEquals(2, view.services().size());
    assertEquals("BAGGAGE", view.services().getFirst().serviceType());
    assertEquals("SINGLE", view.services().getFirst().selectionMode());
    assertEquals("Extra 15kg", view.services().getFirst().options().getFirst().name());
    assertEquals("Extra 20kg", view.services().getFirst().options().get(1).name());
    assertEquals("MEAL", view.services().get(1).serviceType());
    assertEquals("MULTI", view.services().get(1).selectionMode());
    assertEquals("Kids Pasta Set", view.services().get(1).options().getFirst().name());
    assertEquals("VND", view.services().get(1).options().getFirst().currency());
    assertTrue(view.services().get(1).options().getFirst().isActive());
  }

  @Test
  void getCatalog_excludesServicesWithoutActiveOptions() {
    AncillaryCatalogService service =
        new AncillaryCatalogService(
            serviceRepository,
            serviceOptionRepository,
            flightFareRepository,
            countryCurrencyResolver);
    ServiceEntity baggage = serviceEntity(1L, "BAGGAGE", "Baggage Add-on", "bag.png", true);
    ServiceEntity lounge = serviceEntity(2L, "LOUNGE", "Airport Lounge", "lounge.png", true);

    when(serviceRepository.findAllByIsActiveTrueOrderByTypeAscNameAsc())
        .thenReturn(List.of(baggage, lounge));
    when(serviceOptionRepository.findActiveByServiceIds(anyCollection()))
        .thenReturn(
            List.of(optionEntity(11L, baggage, "Extra 15kg", "Checked bag 15kg", "180000", true)));

    AncillaryCatalogService.AncillaryCatalogView view = service.getCatalog();

    assertEquals(1, view.services().size());
    assertEquals("BAGGAGE", view.services().getFirst().serviceType());
  }

  @Test
  void getCatalogForFare_usesFareCurrency() {
    AncillaryCatalogService service =
        new AncillaryCatalogService(
            serviceRepository,
            serviceOptionRepository,
            flightFareRepository,
            countryCurrencyResolver);
    ServiceEntity baggage = serviceEntity(1L, "BAGGAGE", "Baggage Add-on", "bag.png", true);
    FlightFareEntity fare = fare(11L, 101L, "US", "United States");

    when(flightFareRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(fare));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("US");
    when(countryCurrencyResolver.resolveCurrency("US")).thenReturn("USD");
    when(serviceRepository.findAllByIsActiveTrueOrderByTypeAscNameAsc())
        .thenReturn(List.of(baggage));
    when(serviceOptionRepository.findActiveByServiceIds(anyCollection()))
        .thenReturn(
            List.of(optionEntity(11L, baggage, "Extra 15kg", "Checked bag 15kg", "180000", true)));

    AncillaryCatalogService.AncillaryCatalogView view = service.getCatalogForFare(11L, 101L);

    assertEquals("USD", view.currency());
    assertEquals("USD", view.services().getFirst().options().getFirst().currency());
  }

  @Test
  void getCatalogForFare_removesLoungeAndPriorityWhenIncludedInFare() {
    AncillaryCatalogService service =
        new AncillaryCatalogService(
            serviceRepository,
            serviceOptionRepository,
            flightFareRepository,
            countryCurrencyResolver);
    ServiceEntity baggage = serviceEntity(1L, "BAGGAGE", "Baggage Add-on", "bag.png", true);
    ServiceEntity lounge = serviceEntity(2L, "LOUNGE", "Airport Lounge", "lounge.png", true);
    ServiceEntity priority =
        serviceEntity(3L, "PRIORITY_BOARDING", "Priority Boarding", "priority.png", true);
    ServiceEntity meal = serviceEntity(4L, "MEAL", "Pre-order Meals", "meal.png", true);
    FlightFareEntity fare = fare(11L, 101L, "VN", "Vietnam", fareClass(true, true));

    when(flightFareRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(fare));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(serviceRepository.findAllByIsActiveTrueOrderByTypeAscNameAsc())
        .thenReturn(List.of(baggage, lounge, priority, meal));
    when(serviceOptionRepository.findActiveByServiceIds(anyCollection()))
        .thenReturn(
            List.of(
                optionEntity(11L, baggage, "Extra 15kg", "Checked bag 15kg", "180000", true),
                optionEntity(41L, meal, "Kids Pasta Set", "Child meal", "85000", true)));

    AncillaryCatalogService.AncillaryCatalogView view = service.getCatalogForFare(11L, 101L);

    assertEquals(List.of("BAGGAGE", "MEAL"), serviceTypes(view));
  }

  @Test
  void getCatalogForFare_removesPriorityButKeepsLoungeWhenOnlyPriorityIncluded() {
    AncillaryCatalogService service =
        new AncillaryCatalogService(
            serviceRepository,
            serviceOptionRepository,
            flightFareRepository,
            countryCurrencyResolver);
    ServiceEntity lounge = serviceEntity(2L, "LOUNGE", "Airport Lounge", "lounge.png", true);
    ServiceEntity priority =
        serviceEntity(3L, "PRIORITY_BOARDING", "Priority Boarding", "priority.png", true);
    FlightFareEntity fare = fare(11L, 101L, "VN", "Vietnam", fareClass(false, true));

    when(flightFareRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(fare));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(serviceRepository.findAllByIsActiveTrueOrderByTypeAscNameAsc())
        .thenReturn(List.of(lounge, priority));
    when(serviceOptionRepository.findActiveByServiceIds(anyCollection()))
        .thenReturn(
            List.of(
                optionEntity(
                    21L, lounge, "Domestic Lounge Pass", "Domestic lounge", "350000", true)));

    AncillaryCatalogService.AncillaryCatalogView view = service.getCatalogForFare(11L, 101L);

    assertEquals(List.of("LOUNGE"), serviceTypes(view));
  }

  @Test
  void getCatalogForFare_keepsLoungeAndPriorityWhenNotIncludedInFare() {
    AncillaryCatalogService service =
        new AncillaryCatalogService(
            serviceRepository,
            serviceOptionRepository,
            flightFareRepository,
            countryCurrencyResolver);
    ServiceEntity lounge = serviceEntity(2L, "LOUNGE", "Airport Lounge", "lounge.png", true);
    ServiceEntity priority =
        serviceEntity(3L, "PRIORITY_BOARDING", "Priority Boarding", "priority.png", true);
    FlightFareEntity fare = fare(11L, 101L, "VN", "Vietnam", fareClass(false, false));

    when(flightFareRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(fare));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(serviceRepository.findAllByIsActiveTrueOrderByTypeAscNameAsc())
        .thenReturn(List.of(lounge, priority));
    when(serviceOptionRepository.findActiveByServiceIds(anyCollection()))
        .thenReturn(
            List.of(
                optionEntity(
                    21L, lounge, "Domestic Lounge Pass", "Domestic lounge", "350000", true),
                optionEntity(
                    31L, priority, "Priority Boarding Standard", "Board earlier", "80000", true)));

    AncillaryCatalogService.AncillaryCatalogView view = service.getCatalogForFare(11L, 101L);

    assertEquals(List.of("LOUNGE", "PRIORITY_BOARDING"), serviceTypes(view));
  }

  @Test
  void getCatalogForFare_requiresFlightAndFareTogether() {
    AncillaryCatalogService service =
        new AncillaryCatalogService(
            serviceRepository,
            serviceOptionRepository,
            flightFareRepository,
            countryCurrencyResolver);

    AppException exception =
        assertThrows(AppException.class, () -> service.getCatalogForFare(11L, null));

    assertEquals("VALIDATION_FAILED", exception.getCode());
  }

  private static ServiceEntity serviceEntity(
      Long id, String type, String name, String iconUrl, boolean isActive) {
    ServiceEntity entity = new ServiceEntity();
    entity.setId(id);
    entity.setType(type);
    entity.setName(name);
    entity.setIconUrl(iconUrl);
    entity.setActive(isActive);
    return entity;
  }

  private static ServiceOptionEntity optionEntity(
      Long id,
      ServiceEntity service,
      String name,
      String description,
      String price,
      boolean isActive) {
    ServiceOptionEntity entity = new ServiceOptionEntity();
    entity.setId(id);
    entity.setService(service);
    entity.setName(name);
    entity.setDescription(description);
    entity.setPrice(new BigDecimal(price));
    entity.setImageUrl(name + ".png");
    entity.setActive(isActive);
    return entity;
  }

  private static FlightFareEntity fare(
      Long flightId, Long fareId, String countryCode, String country) {
    return fare(flightId, fareId, countryCode, country, null);
  }

  private static FlightFareEntity fare(
      Long flightId, Long fareId, String countryCode, String country, FareClassEntity fareClass) {
    AirportEntity airport = new AirportEntity();
    airport.setCountryCode(countryCode);
    airport.setCountry(country);
    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    flight.setDepartureAirport(airport);
    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(fareId);
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    return fare;
  }

  private static FareClassEntity fareClass(boolean loungeAccess, boolean priorityBoarding) {
    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setLoungeAccess(loungeAccess);
    fareClass.setPriorityBoarding(priorityBoarding);
    return fareClass;
  }

  private static List<String> serviceTypes(AncillaryCatalogService.AncillaryCatalogView view) {
    return view.services().stream()
        .map(AncillaryCatalogService.AncillaryServiceGroupView::serviceType)
        .toList();
  }
}
