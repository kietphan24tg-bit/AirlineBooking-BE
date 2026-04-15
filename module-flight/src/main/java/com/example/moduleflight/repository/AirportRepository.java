package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AirportEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AirportRepository extends JpaRepository<AirportEntity, Long> {

  List<AirportEntity> findByCodeIn(Collection<String> codes);

  @Query(
      """
      select a.id as id, a.code as code, a.name as name, a.city as city, a.country as country
      from AirportEntity a
      order by a.country asc, a.name asc
      """)
  List<AirportGroupedProjection> findAllForGroupedByCountry();

  @Query(
      """
      select a.id as id, a.code as code, a.name as name, a.city as city, a.country as country
      from AirportEntity a
      where a.country = :country
      order by a.name asc
      """)
  List<AirportGroupedProjection> findByCountryForGrouped(@Param("country") String country);
}
