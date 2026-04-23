package com.example.appbootstrap.application.booking;

import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.repository.ServiceOptionRepository;
import com.example.modulebooking.repository.ServiceRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AncillaryCatalogService {

  private static final String DEFAULT_CURRENCY = "VND";

  private final ServiceRepository serviceRepository;
  private final ServiceOptionRepository serviceOptionRepository;

  public AncillaryCatalogService(
      ServiceRepository serviceRepository, ServiceOptionRepository serviceOptionRepository) {
    this.serviceRepository = serviceRepository;
    this.serviceOptionRepository = serviceOptionRepository;
  }

  @Transactional(readOnly = true)
  public AncillaryCatalogView getCatalog() {
    List<ServiceEntity> services = serviceRepository.findAllByIsActiveTrueOrderByTypeAscNameAsc();
    if (services.isEmpty()) {
      return new AncillaryCatalogView(DEFAULT_CURRENCY, List.of());
    }

    Map<Long, ServiceEntity> servicesById = new LinkedHashMap<>();
    for (ServiceEntity service : services) {
      if (service.getId() != null) {
        servicesById.put(service.getId(), service);
      }
    }
    if (servicesById.isEmpty()) {
      return new AncillaryCatalogView(DEFAULT_CURRENCY, List.of());
    }

    Map<Long, List<AncillaryOptionView>> optionsByServiceId =
        serviceOptionRepository.findActiveByServiceIds(servicesById.keySet()).stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    option -> option.getService().getId(),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.mapping(
                        this::toOptionView, java.util.stream.Collectors.toList())));

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

    return new AncillaryCatalogView(DEFAULT_CURRENCY, groups);
  }

  private AncillaryOptionView toOptionView(ServiceOptionEntity option) {
    return new AncillaryOptionView(
        option.getId(),
        option.getName(),
        option.getDescription(),
        option.getPrice(),
        DEFAULT_CURRENCY,
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
