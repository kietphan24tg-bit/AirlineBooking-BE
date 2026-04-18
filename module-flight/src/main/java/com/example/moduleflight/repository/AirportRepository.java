package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AirportEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AirportRepository extends JpaRepository<AirportEntity, Long> {

  List<AirportEntity> findByCodeIn(Collection<String> codes);

  Optional<AirportEntity> findFirstByCodeIgnoreCase(String code);

  @Query(
      """
      select a
      from AirportEntity a
      where lower(a.code) = :term
         or lower(a.code) like :prefix
         or lower(a.city) like :prefix
         or lower(a.name) like :contains
         or lower(a.country) like :contains
      """)
  List<AirportEntity> searchCandidates(
      @Param("term") String term,
      @Param("prefix") String prefix,
      @Param("contains") String contains);

  @Query(
      """
      select a
      from AirportEntity a
      where lower(a.country) = lower(:country)
      order by a.city asc, a.name asc, a.code asc
      """)
  List<AirportEntity> findByCountryIgnoreCaseOrderByCityNameCode(@Param("country") String country);

  @Query(
      """
      select a
      from AirportEntity a
      where upper(a.countryCode) = upper(:countryCode)
      order by a.city asc, a.name asc, a.code asc
      """)
  List<AirportEntity> findByCountryCodeIgnoreCaseOrderByCityNameCode(
      @Param("countryCode") String countryCode);

  @Query(
      """
      select a.id as id, a.code as code, a.name as name, a.city as city, a.country as country,
             a.countryCode as countryCode
      from AirportEntity a
      order by a.country asc, a.name asc
      """)
  List<AirportGroupedProjection> findAllForGroupedByCountry();

  @Query(
      """
      select a.id as id, a.code as code, a.name as name, a.city as city, a.country as country,
             a.countryCode as countryCode
      from AirportEntity a
      where a.country = :country
      order by a.name asc
      """)
  List<AirportGroupedProjection> findByCountryForGrouped(@Param("country") String country);
}
