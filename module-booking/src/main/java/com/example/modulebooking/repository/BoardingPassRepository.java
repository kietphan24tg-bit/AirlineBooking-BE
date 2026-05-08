package com.example.modulebooking.repository;

import com.example.modulebooking.entity.BoardingPassEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardingPassRepository extends JpaRepository<BoardingPassEntity, Long> {

  @Query(
      """
      select bp
      from BoardingPassEntity bp
        join fetch bp.ticket t
      where t.id in :ticketIds
      """)
  List<BoardingPassEntity> findByTicketIdIn(@Param("ticketIds") Collection<Long> ticketIds);
}
