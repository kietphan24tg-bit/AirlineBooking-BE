package com.example.appbootstrap.application.booking;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.repository.ServiceOptionRepository;
import com.example.modulebooking.repository.ServiceRepository;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FlightFareRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AncillaryCatalogService {

  private static final String DEFAULT_CURRENCY = "VND";

  private final ServiceRepository serviceRepository;
  private final ServiceOptionRepository serviceOptionRepository;
  private final FlightFareRepository flightFareRepository;
  private final CountryCurrencyResolver countryCurrencyResolver;

  public AncillaryCatalogService(
      ServiceRepository serviceRepository,
      ServiceOptionRepository serviceOptionRepository,
      FlightFareRepository flightFareRepository,
      CountryCurrencyResolver countryCurrencyResolver) {
    this.serviceRepository = serviceRepository;
    this.serviceOptionRepository = serviceOptionRepository;
    this.flightFareRepository = flightFareRepository;
    this.countryCurrencyResolver = countryCurrencyResolver;
  }

  @Transactional(readOnly = true)
  public AncillaryCatalogView getCatalog() {
    return getCatalog(DEFAULT_CURRENCY);
  }

  @Transactional(readOnly = true)
  public AncillaryCatalogView getCatalogForFare(Long flightId, Long fareId) {
    if (flightId == null && fareId == null) {
      return getCatalog();
    }
    if (flightId == null || fareId == null) {
      throw new AppException(
          "VALIDATION_FAILED", "flightId and fareId must be provided together", 400);
    }
    FlightFareEntity fare =
        flightFareRepository
            .findByIdWithDetails(fareId)
            .orElseThrow(() -> new AppException("FARE_NOT_FOUND", "Fare not found", 404));
    if (fare.getFlight() == null || !Objects.equals(flightId, fare.getFlight().getId())) {
      throw new AppException("FARE_NOT_FOUND", "Fare does not belong to the requested flight", 404);
    }
    String countryCode = countryCurrencyResolver.resolveCountryCode(fare);
    return getCatalog(countryCurrencyResolver.resolveCurrency(countryCode));
  }

  private AncillaryCatalogView getCatalog(String currency) {
    List<ServiceEntity> services = serviceRepository.findAllByIsActiveTrueOrderByTypeAscNameAsc();
    if (services.isEmpty()) {
      return new AncillaryCatalogView(currency, List.of());
    }

    Map<Long, ServiceEntity> servicesById = new LinkedHashMap<>();
    for (ServiceEntity service : services) {
      if (service.getId() != null) {
        servicesById.put(service.getId(), service);
      }
    }
    if (servicesById.isEmpty()) {
      return new AncillaryCatalogView(currency, List.of());
    }

    Map<Long, List<AncillaryOptionView>> optionsByServiceId =
        serviceOptionRepository.findActiveByServiceIds(servicesById.keySet()).stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    option -> option.getService().getId(),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.mapping(
                        option -> toOptionView(option, currency),
                        java.util.stream.Collectors.toList())));

    List<AncillaryServiceGroupView> groups =
        servicesById.entrySet().stream()
            .map(
                entry -> {
                  List<AncillaryOptionView> options = optionsByServiceId.get(entry.getKey());
                  if (options == null || options.isEmpty()) {
                    return null;
                  }
                  ServiceEntity service = entry.getValue();
                  return new AncillaryServiceGroupView(
                      service.getId(),
                      service.getType(),
                      service.getName(),
                      service.getIconUrl(),
                      resolveSelectionMode(service.getType()),
                      options);
                })
            .filter(java.util.Objects::nonNull)
            .toList();

    return new AncillaryCatalogView(currency, groups);
  }

  private AncillaryOptionView toOptionView(ServiceOptionEntity option, String currency) {
    return new AncillaryOptionView(
        option.getId(),
        option.getName(),
        option.getDescription(),
        option.getPrice(),
        currency,
        option.getImageUrl(),
        option.isActive());
  }

  private String resolveSelectionMode(String serviceType) {
    if ("MEAL".equalsIgnoreCase(serviceType)) {
      return "MULTI";
    }
    return "SINGLE";
  }

  public record AncillaryCatalogView(String currency, List<AncillaryServiceGroupView> services) {}

  public record AncillaryServiceGroupView(
      Long serviceId,
      String serviceType,
      String serviceName,
      String iconUrl,
      String selectionMode,
      List<AncillaryOptionView> options) {}

  public record AncillaryOptionView(
      Long optionId,
      String name,
      String description,
      BigDecimal price,
      String currency,
      String imageUrl,
      boolean isActive) {}
}
