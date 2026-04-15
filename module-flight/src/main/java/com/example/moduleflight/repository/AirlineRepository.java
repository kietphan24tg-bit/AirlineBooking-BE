package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AirlineEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AirlineRepository extends JpaRepository<AirlineEntity, Long> {

  Optional<AirlineEntity> findByCode(String code);

  List<AirlineEntity> findByCodeIn(Collection<String> codes);

  @Query("select a.code from AirlineEntity a where a.code is not null and trim(a.code) <> ''")
  List<String> findAllCodes();
}
