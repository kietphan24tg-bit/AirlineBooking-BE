package com.example.moduleflight.application;

import com.example.moduleflight.entity.PromotionSnapshotEntity;
import com.example.moduleflight.entity.PromotionValueSnapshotEntity;
import com.example.moduleflight.repository.PromotionSnapshotRepository;
import com.example.moduleflight.repository.PromotionValueSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PromotionPreviewService {

  private static final String COUNTRY_GLOBAL = "GL";
  private static final String DISCOUNT_TYPE_PERCENT = "PERCENT";
  private static final String DISCOUNT_TYPE_FIXED = "FIXED";

  private final PromotionSnapshotRepository promotionSnapshotRepository;
  private final PromotionValueSnapshotRepository promotionValueSnapshotRepository;

  public PromotionPreviewService(
      PromotionSnapshotRepository promotionSnapshotRepository,
      PromotionValueSnapshotRepository promotionValueSnapshotRepository) {
    this.promotionSnapshotRepository = promotionSnapshotRepository;
    this.promotionValueSnapshotRepository = promotionValueSnapshotRepository;
  }

  public PromoPreview preview(
      String promoCode,
      BigDecimal originalPrice,
      String currency,
      String countryCode,
      boolean fareAvailable) {
    Objects.requireNonNull(originalPrice, "originalPrice must not be null");
    if (!StringUtils.hasText(promoCode)) {
      return PromoPreview.notApplicable("No promo code provided", originalPrice);
    }
    PromotionSnapshotEntity promotion =
        promotionSnapshotRepository.findByCodeIgnoreCase(promoCode.trim()).orElse(null);
    if (promotion == null) {
      return PromoPreview.notApplicable("Promo code not found", originalPrice);
    }
    if (!promotion.isActive()) {
      return PromoPreview.notApplicable("Promo code is disabled", originalPrice);
    }
    OffsetDateTime now = OffsetDateTime.now();
    if (promotion.getStartsAt() != null && now.isBefore(promotion.getStartsAt())) {
      return PromoPreview.notApplicable("Promo code is not active yet", originalPrice);
    }
    if (promotion.getExpiresAt() != null && now.isAfter(promotion.getExpiresAt())) {
      return PromoPreview.notApplicable("Promo code has expired", originalPrice);
    }
    if (promotion.getUsageLimit() != null
        && promotion.getUsedCount() != null
        && promotion.getUsedCount() >= promotion.getUsageLimit()) {
      return PromoPreview.notApplicable("Promo code usage limit reached", originalPrice);
    }
    if (promotion.getMinBookingAmount() != null
        && originalPrice.compareTo(promotion.getMinBookingAmount()) < 0) {
      return PromoPreview.notApplicable("Promo minimum amount is not reached", originalPrice);
    }

    PromotionValueSnapshotEntity scopedValue =
        resolvePromotionValue(
            promotion, normalizeCurrency(currency), normalizeCountry(countryCode));
    BigDecimal discountAmount = calculateDiscount(promotion, scopedValue, originalPrice);
    if (discountAmount == null) {
      return PromoPreview.mayApply("Promo may apply at booking step", originalPrice);
    }
    if (discountAmount.signum() <= 0) {
      return PromoPreview.notApplicable("Promo discount is not applicable", originalPrice);
    }
    BigDecimal discounted = originalPrice.subtract(discountAmount).max(BigDecimal.ZERO);
    int discountPercent =
        originalPrice.signum() == 0
            ? 0
            : discountAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.HALF_UP)
                .intValue();

    if (!fareAvailable) {
      return new PromoPreview(
          "MAY_APPLY",
          "Promo available but fare class is sold out",
          originalPrice,
          null,
          BigDecimal.ZERO,
          null);
    }

    return new PromoPreview(
        "APPLIED",
        "Promo applied successfully",
        originalPrice,
        discounted,
        discountAmount,
        discountPercent > 0 ? discountPercent : null);
  }

  private PromotionValueSnapshotEntity resolvePromotionValue(
      PromotionSnapshotEntity promotion, String currency, String countryCode) {
    PromotionValueSnapshotEntity scoped =
        promotionValueSnapshotRepository
            .findFirstByPromotionAndCurrencyIgnoreCaseAndCountryCodeIgnoreCase(
                promotion, currency, countryCode)
            .orElse(null);
    if (scoped != null) {
      return scoped;
    }
    return promotionValueSnapshotRepository
        .findFirstByPromotionAndCurrencyIgnoreCaseAndCountryCodeIgnoreCase(
            promotion, currency, COUNTRY_GLOBAL)
        .orElse(null);
  }

  private BigDecimal calculateDiscount(
      PromotionSnapshotEntity promotion,
      PromotionValueSnapshotEntity valueScope,
      BigDecimal originalPrice) {
    String type = promotion.getDiscountType() == null ? "" : promotion.getDiscountType().trim();
    if (DISCOUNT_TYPE_PERCENT.equalsIgnoreCase(type)) {
      BigDecimal percent = promotion.getDiscountValue();
      if (percent == null) {
        return null;
      }
      BigDecimal discount =
          originalPrice.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      BigDecimal maxDiscount = null;
      if (valueScope != null && valueScope.getMaxDiscountValue() != null) {
        maxDiscount = valueScope.getMaxDiscountValue();
      } else if (promotion.getMaxDiscount() != null) {
        maxDiscount = promotion.getMaxDiscount();
      }
      if (maxDiscount != null && discount.compareTo(maxDiscount) > 0) {
        discount = maxDiscount;
      }
      return discount;
    }
    if (DISCOUNT_TYPE_FIXED.equalsIgnoreCase(type)) {
      if (valueScope != null && valueScope.getFixedDiscountValue() != null) {
        return valueScope.getFixedDiscountValue();
      }
      return promotion.getDiscountValue();
    }
    return null;
  }

  private String normalizeCurrency(String currency) {
    if (!StringUtils.hasText(currency)) {
      return "VND";
    }
    return currency.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeCountry(String countryCode) {
    if (!StringUtils.hasText(countryCode)) {
      return COUNTRY_GLOBAL;
    }
    return countryCode.trim().toUpperCase(Locale.ROOT);
  }

  public record PromoPreview(
      String status,
      String message,
      BigDecimal originalPrice,
      BigDecimal discountedPrice,
      BigDecimal discountAmount,
      Integer discountPercent) {
    public static PromoPreview notApplicable(String message, BigDecimal originalPrice) {
      return new PromoPreview(
          "NOT_APPLICABLE",
          message,
          Objects.requireNonNullElse(originalPrice, BigDecimal.ZERO),
          null,
          BigDecimal.ZERO,
          null);
    }

    public static PromoPreview mayApply(String message, BigDecimal originalPrice) {
      return new PromoPreview(
          "MAY_APPLY",
          message,
          Objects.requireNonNullElse(originalPrice, BigDecimal.ZERO),
          null,
          BigDecimal.ZERO,
          null);
    }
  }
}
