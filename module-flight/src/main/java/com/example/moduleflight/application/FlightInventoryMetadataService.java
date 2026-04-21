package com.example.moduleflight.application;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightInventoryMetadataService {

  private static final String DELETE_NON_CANONICAL_MAPPINGS_SQL =
      """
      delete from fare_class_seat_classes fcsc
      using fare_classes fc, seat_classes sc
      where fcsc.fare_class_id = fc.id
        and fcsc.seat_class_id = sc.id
        and (
          (upper(coalesce(fc.cabin_class, '')) in ('ECONOMY', 'PREMIUM_ECONOMY') and sc.code <> 'ECONOMY')
          or
          (upper(coalesce(fc.cabin_class, '')) in ('BUSINESS', 'FIRST') and sc.code <> 'BUSINESS')
        )
      """;

  private static final String INSERT_CANONICAL_MAPPINGS_SQL =
      """
      insert into fare_class_seat_classes (fare_class_id, seat_class_id)
      select fc.id,
             sc.id
      from fare_classes fc
      join seat_classes sc
        on sc.code = case
                        when upper(coalesce(fc.cabin_class, '')) in ('ECONOMY', 'PREMIUM_ECONOMY') then 'ECONOMY'
                        when upper(coalesce(fc.cabin_class, '')) in ('BUSINESS', 'FIRST') then 'BUSINESS'
                        else null
                     end
      on conflict (fare_class_id, seat_class_id) do nothing
      """;

  private static final String MARK_UNSUPPORTED_FARES_SOLD_OUT_SQL =
      """
      update flight_fares ff
      set status = 'SOLD_OUT',
          updated_at = ?
      from flights f, fare_classes fc
      where ff.flight_id = f.id
        and ff.fare_class_id = fc.id
        and f.id = ff.flight_id
        and f.flight_date >= current_date
        and upper(coalesce(f.status, '')) in ('SCHEDULED', 'ON_TIME')
        and upper(coalesce(ff.status, '')) = 'AVAILABLE'
        and not exists (
          select 1
          from aircraft_layout al
          join aircraft_layout_zones z on z.aircraft_layout_id = al.id
          join seat_classes sc on sc.id = z.seat_class_id
          where al.aircraft_id = f.aircraft_id
            and sc.code = case
                            when upper(coalesce(fc.cabin_class, '')) in ('ECONOMY', 'PREMIUM_ECONOMY') then 'ECONOMY'
                            when upper(coalesce(fc.cabin_class, '')) in ('BUSINESS', 'FIRST') then 'BUSINESS'
                            else null
                          end
        )
      """;

  private final JdbcTemplate jdbcTemplate;

  public FlightInventoryMetadataService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public InventoryMetadataRepairResult repairMetadataForSaleability() {
    int deletedMappings = jdbcTemplate.update(DELETE_NON_CANONICAL_MAPPINGS_SQL);
    int insertedMappings = jdbcTemplate.update(INSERT_CANONICAL_MAPPINGS_SQL);
    int soldOutFares =
        jdbcTemplate.update(
            MARK_UNSUPPORTED_FARES_SOLD_OUT_SQL,
            Timestamp.from(OffsetDateTime.now().toInstant()));
    return new InventoryMetadataRepairResult(deletedMappings, insertedMappings, soldOutFares);
  }

  public String canonicalSeatClassCode(String cabinClass) {
    String normalized = normalize(cabinClass);
    return switch (normalized) {
      case "ECONOMY", "PREMIUM_ECONOMY" -> "ECONOMY";
      case "BUSINESS", "FIRST" -> "BUSINESS";
      default -> null;
    };
  }

  private static String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase().replace(' ', '_');
  }

  public record InventoryMetadataRepairResult(
      int deletedMappings, int insertedMappings, int soldOutFares) {}
}
