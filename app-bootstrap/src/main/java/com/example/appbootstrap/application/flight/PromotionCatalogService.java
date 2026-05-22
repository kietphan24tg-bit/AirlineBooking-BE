package com.example.appbootstrap.application.flight;

import com.example.modulebooking.entity.PromotionEntity;
import com.example.modulebooking.repository.PromotionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionCatalogService {

  private final PromotionRepository promotionRepository;

  public PromotionCatalogService(PromotionRepository promotionRepository) {
    this.promotionRepository = promotionRepository;
  }

  @Transactional(readOnly = true)
  public List<PromotionCardView> getActivePromotions() {
    return promotionRepository.findDisplayableActivePromotions(OffsetDateTime.now()).stream()
        .map(this::toView)
        .toList();
  }

  private PromotionCardView toView(PromotionEntity promotion) {
    return new PromotionCardView(
        promotion.getCode(),
        promotion.getDescription(),
        promotion.getDiscountType(),
        promotion.getDiscountValue(),
        promotion.getMaxDiscount(),
        promotion.getMinBookingAmount(),
        promotion.getExpiresAt());
  }

  public record PromotionCardView(
      String code,
      String description,
      String discountType,
      BigDecimal discountValue,
      BigDecimal maxDiscount,
      BigDecimal minBookingAmount,
      OffsetDateTime expiresAt) {}
}
