package com.example.appbootstrap.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.repository.ServiceOptionRepository;
import com.example.modulebooking.repository.ServiceRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AncillaryCatalogServiceTest {

  @Mock private ServiceRepository serviceRepository;
  @Mock private ServiceOptionRepository serviceOptionRepository;

  @Test
  void getCatalog_returnsActiveGroupedOptionsWithStableOrdering() {
    AncillaryCatalogService service =
        new AncillaryCatalogService(serviceRepository, serviceOptionRepository);
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
        new AncillaryCatalogService(serviceRepository, serviceOptionRepository);
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
}
