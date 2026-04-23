package com.example.appbootstrap.controller.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.application.booking.AncillaryCatalogService;
import com.example.appbootstrap.controller.booking.dto.AncillaryCatalogResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicesControllerTest {

  @Mock private AncillaryCatalogService ancillaryCatalogService;

  private ServicesController controller;

  @BeforeEach
  void setUp() {
    controller = new ServicesController(ancillaryCatalogService);
  }

  @Test
  void getCatalog_mapsServiceResponse() {
    when(ancillaryCatalogService.getCatalog())
        .thenReturn(
            new AncillaryCatalogService.AncillaryCatalogView(
                "VND",
                List.of(
                    new AncillaryCatalogService.AncillaryServiceGroupView(
                        1L,
                        "BAGGAGE",
                        "Baggage Add-on",
                        "bag.png",
                        "SINGLE",
                        List.of(
                            new AncillaryCatalogService.AncillaryOptionView(
                                11L,
                                "Extra 15kg",
                                "Checked bag 15kg",
                                new BigDecimal("180000"),
                                "VND",
                                "extra15.png",
                                true))))));

    AncillaryCatalogResponse response = controller.getCatalog();

    assertEquals("VND", response.currency());
    assertEquals(1, response.services().size());
    assertEquals("BAGGAGE", response.services().getFirst().serviceType());
    assertEquals("SINGLE", response.services().getFirst().selectionMode());
    assertEquals("Extra 15kg", response.services().getFirst().options().getFirst().name());
    assertEquals(new BigDecimal("180000"), response.services().getFirst().options().getFirst().price());
  }
}
