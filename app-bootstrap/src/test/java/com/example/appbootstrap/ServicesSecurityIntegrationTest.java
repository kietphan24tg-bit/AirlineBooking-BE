package com.example.appbootstrap;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.moduleweb.application.booking.AncillaryCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ServicesSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AncillaryCatalogService ancillaryCatalogService;

  @Test
  void shouldExposeServicesCatalogWithoutAuthentication() throws Exception {
    when(ancillaryCatalogService.getCatalogForFare(null, null))
        .thenReturn(new AncillaryCatalogService.AncillaryCatalogView("VND", List.of()));

    mockMvc
        .perform(get("/api/services"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.currency").value("VND"))
        .andExpect(jsonPath("$.message").value("Service catalog returned"));
  }
}
