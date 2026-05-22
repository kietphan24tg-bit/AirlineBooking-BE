package com.example.moduleflight.application;

import com.example.moduleflight.entity.PromotionSnapshotEntity;
import com.example.moduleflight.entity.PromotionValueSnapshotEntity;
import com.example.moduleflight.repository.PromotionSnapshotRepository;
import com.example.moduleflight.repository.PromotionValueSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PromotionBatchPreviewService {

  private static final String COUNTRY_GLOBAL = "GL";

  private final PromotionSnapshotRepository promotionSnapshotRepository;
  private final PromotionValueSnapshotRepository promotionValueSnapshotRepository;

  public PromotionBatchPreviewService(
      PromotionSnapshotRepository promotionSnapshotRepository,
      PromotionValueSnapshotRepository promotionValueSnapshotRepository) {
    this.promotionSnapshotRepository = promotionSnapshotRepository;
    this.promotionValueSnapshotRepository = promotionValueSnapshotRepository;
  }

  public PromotionContext loadContext(String promoCode) {
    if (!StringUtils.hasText(promoCode)) {
      return PromotionContext.empty();
    }
    PromotionSnapshotEntity promotion =
        promotionSnapshotRepository.findByCodeIgnoreCase(promoCode.trim()).orElse(null);
    if (promotion == null) {
      return PromotionContext.withMessage("NOT_APPLICABLE", "Promo code not found");
    }
    if (!promotion.isActive()) {
      return PromotionContext.withMessage("NOT_APPLICABLE", "Promo code is disabled");
    }
    OffsetDateTime now = OffsetDateTime.now();
    if (promotion.getStartsAt() != null && now.isBefore(promotion.getStartsAt())) {
      return PromotionContext.withMessage("NOT_APPLICABLE", "Promo code is not active yet");
    }
    if (promotion.getExpiresAt() != null && now.isAfter(promotion.getExpiresAt())) {
      return PromotionContext.withMessage("NOT_APPLICABLE", "Promo code has expired");
    }
    if (promotion.getUsageLimit() != null
        && promotion.getUsedCount() != null
        && promotion.getUsedCount() >= promotion.getUsageLimit()) {
      return PromotionContext.withMessage("NOT_APPLICABLE", "Promo code usage limit reached");
    }

    Map<String, PromotionValueSnapshotEntity> valuesByScope = new HashMap<>();
    List<PromotionValueSnapshotEntity> scopedValues =
        promotionValueSnapshotRepository.findAllByPromotion(promotion);
    for (PromotionValueSnapshotEntity scopedValue : scopedValues) {
      String key =
          normalizeCurrency(scopedValue.getCurrency())
              + ":"
              + normalizeCountry(scopedValue.getCountryCode());
      valuesByScope.put(key, scopedValue);
    }
    return new PromotionContext(promotion, valuesByScope, null, null);
  }

  public PromoPreview preview(
      PromotionContext context,
      BigDecimal originalPrice,
      String currency,
      String countryCode,
      boolean fareAvailable) {
    if (originalPrice == null) {
      return PromoPreview.notApplicable("Original price is required", null);
    }
    String normalizedCurrency = normalizeCurrency(currency);
    if (context.promotion == null) {
      return new PromoPreview(
          context.fallbackStatus,
          context.fallbackMessage,
          originalPrice,
          null,
          BigDecimal.ZERO,
          null);
    }
    if (context.promotion.getMinBookingAmount() != null
        && originalPrice.compareTo(context.promotion.getMinBookingAmount()) < 0) {
      return PromoPreview.notApplicable("Promo minimum amount is not reached", originalPrice);
    }

    PromotionValueSnapshotEntity scopedValue =
        resolveScopedValue(context, normalizedCurrency, countryCode);
    BigDecimal discountAmount =
        calculateDiscount(context.promotion, scopedValue, originalPrice, normalizedCurrency);
    if (discountAmount == null) {
      return PromoPreview.mayApply("Promo may apply at booking step", originalPrice);
    }
    if (discountAmount.signum() <= 0) {
      return PromoPreview.notApplicable("Promo discount is not applicable", originalPrice);
    }

    if (!fareAvailable) {
      return PromoPreview.mayApply("Promo available but fare class is sold out", originalPrice);
    }

    BigDecimal clampedDiscount =
        CurrencyAmountNormalizer.normalize(
            discountAmount.min(originalPrice), normalizedCurrency);
    BigDecimal discountedPrice =
        CurrencyAmountNormalizer.normalize(
            originalPrice.subtract(clampedDiscount).max(BigDecimal.ZERO), normalizedCurrency);
    Integer discountPercent =
        originalPrice.signum() == 0
            ? null
            : clampedDiscount
                .multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    return new PromoPreview(
        "APPLIED",
        "Promo applied successfully",
        originalPrice,
        discountedPrice,
        clampedDiscount,
        discountPercent);
  }

  private PromotionValueSnapshotEntity resolveScopedValue(
      PromotionContext context, String currency, String countryCode) {
    String currencyCode = normalizeCurrency(currency);
    String country = normalizeCountry(countryCode);
    PromotionValueSnapshotEntity scoped = context.valuesByScope.get(currencyCode + ":" + country);
    if (scoped != null) {
      return scoped;
    }
    return context.valuesByScope.get(currencyCode + ":" + COUNTRY_GLOBAL);
  }

  private BigDecimal calculateDiscount(
      PromotionSnapshotEntity promotion,
      PromotionValueSnapshotEntity scopedValue,
      BigDecimal originalPrice,
      String currency) {
    String discountType =
        promotion.getDiscountType() == null
            ? ""
            : promotion.getDiscountType().trim().toUpperCase(Locale.ROOT);
    if ("PERCENT".equals(discountType)) {
      BigDecimal percent = promotion.getDiscountValue();
      if (percent == null) {
        return null;
      }
      BigDecimal discount =
          originalPrice
              .multiply(percent)
              .divide(
                  BigDecimal.valueOf(100),
                  CurrencyAmountNormalizer.scaleFor(currency),
                  RoundingMode.HALF_UP);
      BigDecimal maxDiscount = promotion.getMaxDiscount();
      if (scopedValue != null && scopedValue.getMaxDiscountValue() != null) {
        maxDiscount = scopedValue.getMaxDiscountValue();
      }
      if (maxDiscount != null && discount.compareTo(maxDiscount) > 0) {
        discount = maxDiscount;
      }
      return CurrencyAmountNormalizer.normalize(discount, currency);
    }
    if ("FIXED".equals(discountType)) {
      if (scopedValue != null && scopedValue.getFixedDiscountValue() != null) {
        return CurrencyAmountNormalizer.normalize(scopedValue.getFixedDiscountValue(), currency);
      }
      return CurrencyAmountNormalizer.normalize(promotion.getDiscountValue(), currency);
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

  public static final class PromotionContext {
    private final PromotionSnapshotEntity promotion;
    private final Map<String, PromotionValueSnapshotEntity> valuesByScope;
    private final String fallbackStatus;
    private final String fallbackMessage;

    private PromotionContext(
        PromotionSnapshotEntity promotion,
        Map<String, PromotionValueSnapshotEntity> valuesByScope,
        String fallbackStatus,
        String fallbackMessage) {
      this.promotion = promotion;
      this.valuesByScope = valuesByScope;
      this.fallbackStatus = fallbackStatus;
      this.fallbackMessage = fallbackMessage;
    }

    public static PromotionContext empty() {
      return withMessage("NOT_APPLICABLE", "Promo code not provided");
    }

    public static PromotionContext withMessage(String status, String message) {
      return new PromotionContext(null, Map.of(), status, message);
    }
  }

  public record PromoPreview(
      String status,
      String message,
      BigDecimal originalPrice,
      BigDecimal discountedPrice,
      BigDecimal discountAmount,
      Integer discountPercent) {
    public static PromoPreview notApplicable(String message, BigDecimal originalPrice) {
      BigDecimal price = Objects.requireNonNullElse(originalPrice, BigDecimal.ZERO);
      return new PromoPreview("NOT_APPLICABLE", message, price, null, BigDecimal.ZERO, null);
    }

    public static PromoPreview mayApply(String message, BigDecimal originalPrice) {
      BigDecimal price = Objects.requireNonNullElse(originalPrice, BigDecimal.ZERO);
      return new PromoPreview("MAY_APPLY", message, price, null, BigDecimal.ZERO, null);
    }
  }
}
