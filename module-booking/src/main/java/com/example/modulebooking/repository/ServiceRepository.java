package com.example.modulebooking.repository;

import com.example.modulebooking.entity.ServiceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

  List<ServiceEntity> findAllByIsActiveTrueOrderByTypeAscNameAsc();
}
