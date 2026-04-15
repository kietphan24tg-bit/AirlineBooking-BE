package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.moduleflight.entity.PromotionSnapshotEntity;
import com.example.moduleflight.entity.PromotionValueSnapshotEntity;
import com.example.moduleflight.repository.PromotionSnapshotRepository;
import com.example.moduleflight.repository.PromotionValueSnapshotRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromotionPreviewServiceTest {

  @Mock private PromotionSnapshotRepository promotionSnapshotRepository;
  @Mock private PromotionValueSnapshotRepository promotionValueSnapshotRepository;

  private PromotionPreviewService service;

  @BeforeEach
  void setUp() {
    service =
        new PromotionPreviewService(promotionSnapshotRepository, promotionValueSnapshotRepository);
  }

  @Test
  void preview_appliesPercentWithCurrencyCap() {
    PromotionSnapshotEntity promotion = new PromotionSnapshotEntity();
    promotion.setCode("SAVE20");
    promotion.setDiscountType("PERCENT");
    promotion.setDiscountValue(new BigDecimal("20"));
    promotion.setActive(true);
    promotion.setUsedCount(0);
    promotion.setStartsAt(OffsetDateTime.now().minusDays(1));
    promotion.setExpiresAt(OffsetDateTime.now().plusDays(1));
    when(promotionSnapshotRepository.findByCodeIgnoreCase("SAVE20"))
        .thenReturn(Optional.of(promotion));

    PromotionValueSnapshotEntity value = new PromotionValueSnapshotEntity();
    value.setMaxDiscountValue(new BigDecimal("250000"));
    when(promotionValueSnapshotRepository
            .findFirstByPromotionAndCurrencyIgnoreCaseAndCountryCodeIgnoreCase(
                promotion, "VND", "VN"))
        .thenReturn(Optional.of(value));

    PromotionPreviewService.PromoPreview preview =
        service.preview("SAVE20", new BigDecimal("2000000"), "VND", "VN", true);

    assertEquals("APPLIED", preview.status());
    assertEquals(new BigDecimal("1750000"), preview.discountedPrice());
    assertEquals(13, preview.discountPercent());
  }

  @Test
  void preview_returnsNotApplicableWhenMinAmountNotReached() {
    PromotionSnapshotEntity promotion = new PromotionSnapshotEntity();
    promotion.setCode("SAVE20");
    promotion.setDiscountType("PERCENT");
    promotion.setDiscountValue(new BigDecimal("20"));
    promotion.setMinBookingAmount(new BigDecimal("2000000"));
    promotion.setActive(true);
    promotion.setUsedCount(0);
    promotion.setStartsAt(OffsetDateTime.now().minusDays(1));
    promotion.setExpiresAt(OffsetDateTime.now().plusDays(1));
    when(promotionSnapshotRepository.findByCodeIgnoreCase("SAVE20"))
        .thenReturn(Optional.of(promotion));

    PromotionPreviewService.PromoPreview preview =
        service.preview("SAVE20", new BigDecimal("1000000"), "VND", "VN", true);

    assertEquals("NOT_APPLICABLE", preview.status());
    assertEquals(new BigDecimal("1000000"), preview.originalPrice());
  }

  @Test
  void preview_usesFixedValueFromScope() {
    PromotionSnapshotEntity promotion = new PromotionSnapshotEntity();
    promotion.setCode("FLASH200K");
    promotion.setDiscountType("FIXED");
    promotion.setDiscountValue(new BigDecimal("100000"));
    promotion.setActive(true);
    promotion.setUsedCount(0);
    promotion.setStartsAt(OffsetDateTime.now().minusDays(1));
    promotion.setExpiresAt(OffsetDateTime.now().plusDays(1));
    when(promotionSnapshotRepository.findByCodeIgnoreCase("FLASH200K"))
        .thenReturn(Optional.of(promotion));

    PromotionValueSnapshotEntity value = new PromotionValueSnapshotEntity();
    value.setFixedDiscountValue(new BigDecimal("200000"));
    when(promotionValueSnapshotRepository
            .findFirstByPromotionAndCurrencyIgnoreCaseAndCountryCodeIgnoreCase(
                promotion, "VND", "VN"))
        .thenReturn(Optional.of(value));

    PromotionPreviewService.PromoPreview preview =
        service.preview("FLASH200K", new BigDecimal("1500000"), "VND", "VN", true);

    assertEquals("APPLIED", preview.status());
    assertEquals(new BigDecimal("1300000"), preview.discountedPrice());
    assertEquals(13, preview.discountPercent());
  }

  @Test
  void preview_returnsNotApplicableForExpiredPromo() {
    PromotionSnapshotEntity promotion = new PromotionSnapshotEntity();
    promotion.setCode("OLD");
    promotion.setDiscountType("PERCENT");
    promotion.setDiscountValue(new BigDecimal("10"));
    promotion.setActive(true);
    promotion.setUsedCount(0);
    promotion.setStartsAt(OffsetDateTime.now().minusDays(5));
    promotion.setExpiresAt(OffsetDateTime.now().minusDays(1));
    when(promotionSnapshotRepository.findByCodeIgnoreCase(anyString()))
        .thenReturn(Optional.of(promotion));

    PromotionPreviewService.PromoPreview preview =
        service.preview("OLD", new BigDecimal("1500000"), "VND", "VN", true);

    assertEquals("NOT_APPLICABLE", preview.status());
    assertEquals(new BigDecimal("1500000"), preview.originalPrice());
  }
}
