package com.example.modulepayment.repository;

import com.example.modulepayment.entity.PaymentWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentWebhookEventRepository
    extends JpaRepository<PaymentWebhookEventEntity, Long> {

  boolean existsByProviderAndIdempotencyKey(String provider, String idempotencyKey);
}
