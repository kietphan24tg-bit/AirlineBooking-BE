package com.example.moduleflight.repository;

import com.example.moduleflight.entity.FareClassEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareClassRepository extends JpaRepository<FareClassEntity, Long> {

  Optional<FareClassEntity> findByCode(String code);
}
