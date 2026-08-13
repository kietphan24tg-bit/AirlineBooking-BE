package com.example.moduleweb.controller.flight.dto;

import com.example.moduleweb.application.flight.PromotionCatalogService.PromotionCardView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Active promotion codes available for search and booking flows.")
public record PromotionCatalogResponse(List<PromotionItem> promotions) {

  public static PromotionCatalogResponse from(List<PromotionCardView> promotions) {
    return new PromotionCatalogResponse(promotions.stream().map(PromotionItem::from).toList());
  }

  public record PromotionItem(
      @Schema(example = "WELCOME10") String code,
      @Schema(example = "10% off for newly onboarded users on their first booking.")
          String description,
      @Schema(example = "PERCENT") String discountType,
      @Schema(example = "10") BigDecimal discountValue,
      @Schema(example = "300000") BigDecimal maxDiscount,
      @Schema(example = "1000000") BigDecimal minBookingAmount,
      @Schema(example = "2026-11-18T10:00:00Z") OffsetDateTime expiresAt) {

    public static PromotionItem from(PromotionCardView promotion) {
      return new PromotionItem(
          promotion.code(),
          promotion.description(),
          promotion.discountType(),
          promotion.discountValue(),
          promotion.maxDiscount(),
          promotion.minBookingAmount(),
          promotion.expiresAt());
    }
  }
}
