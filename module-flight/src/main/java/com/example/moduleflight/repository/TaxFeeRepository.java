package com.example.moduleflight.repository;

import com.example.moduleflight.entity.TaxFeeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxFeeRepository extends JpaRepository<TaxFeeEntity, Long> {

  List<TaxFeeEntity> findByIsActiveTrueAndApplyTo(String applyTo);

  List<TaxFeeEntity> findByIsActiveTrueAndApplyToAndTaxProfileCode(String applyTo, String taxProfileCode);
}
