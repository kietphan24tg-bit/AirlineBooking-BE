package com.example.modulebooking.repository;

import com.example.modulebooking.entity.ServiceOptionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceOptionRepository extends JpaRepository<ServiceOptionEntity, Long> {

  @Query(
      """
      select so
      from ServiceOptionEntity so
      join fetch so.service s
      where s.id in :serviceIds
        and s.isActive = true
        and so.isActive = true
      order by s.type asc, s.name asc, so.price asc, so.name asc
      """)
  List<ServiceOptionEntity> findActiveByServiceIds(@Param("serviceIds") Collection<Long> serviceIds);
}
