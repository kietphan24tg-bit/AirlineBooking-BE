package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleflight.entity.PromotionSnapshotEntity;
import com.example.moduleflight.entity.PromotionValueSnapshotEntity;
import com.example.moduleflight.repository.PromotionSnapshotRepository;
import com.example.moduleflight.repository.PromotionValueSnapshotRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromotionBatchPreviewServiceTest {

  @Mock private PromotionSnapshotRepository promotionSnapshotRepository;
  @Mock private PromotionValueSnapshotRepository promotionValueSnapshotRepository;

  private PromotionBatchPreviewService service;

  @BeforeEach
  void setUp() {
    service =
        new PromotionBatchPreviewService(
            promotionSnapshotRepository, promotionValueSnapshotRepository);
  }

  @Test
  void
      loadContext_fetchesPromotionAndValuesOnce_thenPreviewMultipleFaresWithoutExtraRepositoryCalls() {
    PromotionSnapshotEntity promotion = new PromotionSnapshotEntity();
    promotion.setCode("SAVE20");
    promotion.setDiscountType("PERCENT");
    promotion.setDiscountValue(new BigDecimal("20"));
    promotion.setActive(true);
    promotion.setStartsAt(OffsetDateTime.now().minusDays(1));
    promotion.setExpiresAt(OffsetDateTime.now().plusDays(1));
    promotion.setUsedCount(0);
    when(promotionSnapshotRepository.findByCodeIgnoreCase("SAVE20"))
        .thenReturn(Optional.of(promotion));

    PromotionValueSnapshotEntity value = new PromotionValueSnapshotEntity();
    value.setCurrency("VND");
    value.setCountryCode("VN");
    value.setMaxDiscountValue(new BigDecimal("200000"));
    when(promotionValueSnapshotRepository.findAllByPromotion(promotion)).thenReturn(List.of(value));

    PromotionBatchPreviewService.PromotionContext context = service.loadContext("SAVE20");
    PromotionBatchPreviewService.PromoPreview first =
        service.preview(context, new BigDecimal("2000000"), "VND", "VN", true);
    PromotionBatchPreviewService.PromoPreview second =
        service.preview(context, new BigDecimal("3000000"), "VND", "VN", true);

    assertEquals("APPLIED", first.status());
    assertEquals(0, first.discountedPrice().compareTo(new BigDecimal("1800000")));
    assertEquals("APPLIED", second.status());
    assertEquals(0, second.discountedPrice().compareTo(new BigDecimal("2800000")));

    verify(promotionSnapshotRepository, times(1)).findByCodeIgnoreCase("SAVE20");
    verify(promotionValueSnapshotRepository, times(1)).findAllByPromotion(promotion);
  }

  @Test
  void preview_roundsPercentDiscountToWholeNumberForVnd() {
    PromotionSnapshotEntity promotion = new PromotionSnapshotEntity();
    promotion.setCode("SAVE10");
    promotion.setDiscountType("PERCENT");
    promotion.setDiscountValue(new BigDecimal("10.64"));
    promotion.setActive(true);
    promotion.setStartsAt(OffsetDateTime.now().minusDays(1));
    promotion.setExpiresAt(OffsetDateTime.now().plusDays(1));
    promotion.setUsedCount(0);
    when(promotionSnapshotRepository.findByCodeIgnoreCase("SAVE10"))
        .thenReturn(Optional.of(promotion));
    when(promotionValueSnapshotRepository.findAllByPromotion(promotion)).thenReturn(List.of());

    PromotionBatchPreviewService.PromotionContext context = service.loadContext("SAVE10");
    PromotionBatchPreviewService.PromoPreview preview =
        service.preview(context, new BigDecimal("2808054"), "VND", "VN", true);

    assertEquals("APPLIED", preview.status());
    assertEquals(new BigDecimal("298777"), preview.discountAmount());
    assertEquals(new BigDecimal("2509277"), preview.discountedPrice());
  }
}
