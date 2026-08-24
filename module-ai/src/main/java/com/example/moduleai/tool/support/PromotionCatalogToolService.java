package com.example.moduleai.tool.support;

import com.example.modulebooking.entity.PromotionEntity;
import com.example.modulebooking.repository.PromotionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionCatalogToolService {

  private final PromotionRepository promotionRepository;

  public PromotionCatalogToolService(PromotionRepository promotionRepository) {
    this.promotionRepository = promotionRepository;
  }

  @Transactional(readOnly = true)
  public List<PromotionCard> getActivePromotions() {
    return promotionRepository.findDisplayableActivePromotions(OffsetDateTime.now()).stream()
        .map(this::toCard)
        .toList();
  }

  private PromotionCard toCard(PromotionEntity promotion) {
    return new PromotionCard(
        promotion.getCode(),
        promotion.getDescription(),
        promotion.getDiscountType(),
        promotion.getDiscountValue(),
        promotion.getMaxDiscount(),
        promotion.getMinBookingAmount(),
        promotion.getExpiresAt());
  }

  public record PromotionCard(
      String code,
      String description,
      String discountType,
      BigDecimal discountValue,
      BigDecimal maxDiscount,
      BigDecimal minBookingAmount,
      OffsetDateTime expiresAt) {}
}
