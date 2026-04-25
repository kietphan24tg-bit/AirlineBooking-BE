package com.example.appbootstrap.controller.booking;

import com.example.appbootstrap.api.ApiSuccessMessage;
import com.example.appbootstrap.application.booking.AncillaryCatalogService;
import com.example.appbootstrap.controller.booking.dto.AncillaryCatalogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
@Tag(name = "Services", description = "Ancillary service catalog")
public class ServicesController {

  private final AncillaryCatalogService ancillaryCatalogService;

  public ServicesController(AncillaryCatalogService ancillaryCatalogService) {
    this.ancillaryCatalogService = ancillaryCatalogService;
  }

  @GetMapping
  @ApiSuccessMessage("Service catalog returned")
  @Operation(
      summary = "Get ancillary service catalog",
      description =
          "Returns active ancillary services grouped with their active options for UI rendering.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Service catalog returned",
        content = @Content(schema = @Schema(implementation = AncillaryCatalogResponse.class)))
  })
  AncillaryCatalogResponse getCatalog(
      @RequestParam(required = false) Long flightId, @RequestParam(required = false) Long fareId) {
    return AncillaryCatalogResponse.from(
        ancillaryCatalogService.getCatalogForFare(flightId, fareId));
  }
}
