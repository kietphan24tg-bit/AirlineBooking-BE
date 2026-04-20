package com.example.moduleflight.application;

import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.repository.AircraftRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Maps ICAO type designators (e.g. B738, A21N) to internal {@link AircraftEntity} rows when {@code
 * aircraft_icao_mappings} has no exact row.
 */
@Service
public class AircraftIcaoFallbackService {

  private final AircraftRepository aircraftRepository;

  public AircraftIcaoFallbackService(AircraftRepository aircraftRepository) {
    this.aircraftRepository = aircraftRepository;
  }

  /**
   * Returns a best-effort aircraft model for an ICAO code using prefix families aligned with seeded
   * models in Flyway (V21, V34).
   */
  public Optional<AircraftEntity> resolveByIcaoFamily(String rawIcao) {
    if (!StringUtils.hasText(rawIcao)) {
      return Optional.empty();
    }
    String code = rawIcao.trim().toUpperCase(Locale.ROOT);
    if (code.length() < 2) {
      return Optional.empty();
    }
    String prefix3 = code.length() >= 3 ? code.substring(0, 3) : code;
    String prefix2 = code.substring(0, 2);

    Optional<String> model =
        switch (prefix3) {
          case "A20", "A21", "A19", "A31", "A32" -> Optional.of("A320neo");
          case "A33" -> Optional.of("A330-300");
          case "A35" -> Optional.of("A350-900");
          case "B78", "B77", "B74" -> Optional.of("B787-9");
          case "B73", "B38", "B39" -> Optional.of("Boeing 737-800");
          case "AT7", "AT5", "AT4", "DH8" -> Optional.of("ATR 72-600");
          case "E19", "E75", "E70", "CR7", "CR9" -> Optional.of("Embraer E190");
          default -> Optional.empty();
        };

    if (model.isEmpty() && prefix2.equals("AT")) {
      model = Optional.of("ATR 72-600");
    }
    if (model.isEmpty() && (prefix2.equals("E1") || prefix2.equals("E7"))) {
      model = Optional.of("Embraer E190");
    }
    if (model.isEmpty() && prefix2.equals("B7")) {
      model = Optional.of("Boeing 737-800");
    }
    if (model.isEmpty() && prefix2.equals("A3")) {
      model = Optional.of("A330-300");
    }

    return model.flatMap(m -> aircraftRepository.findByModelIgnoreCase(m));
  }
}
