package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.common.exception.AppException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlightPaginationServiceTest {

  private FlightPaginationService service;

  @BeforeEach
  void setUp() {
    service = new FlightPaginationService();
  }

  @Test
  void paginate_supportsVersionedCursorV1() {
    FlightRankKey key1 =
        new FlightRankKey(
            FlightSortBy.RECOMMENDED,
            BigDecimal.ZERO,
            new BigDecimal("100"),
            null,
            OffsetDateTime.parse("2026-05-01T00:00:00Z"),
            1L,
            1L);
    FlightRankKey key2 =
        new FlightRankKey(
            FlightSortBy.RECOMMENDED,
            BigDecimal.ZERO,
            new BigDecimal("200"),
            null,
            OffsetDateTime.parse("2026-05-01T01:00:00Z"),
            2L,
            2L);
    var firstPage =
        service.paginate(List.of(new RankedItem("A", key1), new RankedItem("B", key2)), 1, null);
    assertEquals(1, firstPage.items().size());
    assertEquals("A", firstPage.items().getFirst().name());
    assertNotNull(firstPage.nextCursor());

    var secondPage =
        service.paginate(
            List.of(new RankedItem("A", key1), new RankedItem("B", key2)),
            1,
            firstPage.nextCursor());
    assertEquals(1, secondPage.items().size());
    assertEquals("B", secondPage.items().getFirst().name());
  }

  @Test
  void paginate_resetsToFirstPageWhenCursorNoLongerMatchesCurrentResultSet() {
    FlightRankKey key1 =
        new FlightRankKey(
            FlightSortBy.RECOMMENDED,
            BigDecimal.ZERO,
            new BigDecimal("100"),
            null,
            OffsetDateTime.parse("2026-05-01T00:00:00Z"),
            1L,
            1L);
    FlightRankKey key2 =
        new FlightRankKey(
            FlightSortBy.RECOMMENDED,
            BigDecimal.ZERO,
            new BigDecimal("200"),
            null,
            OffsetDateTime.parse("2026-05-01T01:00:00Z"),
            2L,
            2L);
    FlightRankKey staleKey =
        new FlightRankKey(
            FlightSortBy.RECOMMENDED,
            BigDecimal.ZERO,
            new BigDecimal("900"),
            null,
            OffsetDateTime.parse("2026-05-01T09:00:00Z"),
            9L,
            9L);

    var page =
        service.paginate(
            List.of(new RankedItem("A", key1), new RankedItem("B", key2)),
            1,
            service.encodeCursor(staleKey));

    assertEquals(1, page.items().size());
    assertEquals("A", page.items().getFirst().name());
    assertNotNull(page.nextCursor());
  }

  @Test
  void paginate_resetsToFirstPageWhenCursorUsesDifferentSortMode() {
    FlightRankKey recommendedKey =
        new FlightRankKey(
            FlightSortBy.RECOMMENDED,
            BigDecimal.ZERO,
            new BigDecimal("100"),
            null,
            OffsetDateTime.parse("2026-05-01T00:00:00Z"),
            1L,
            1L);
    FlightRankKey cheapestCursor =
        new FlightRankKey(
            FlightSortBy.CHEAPEST,
            new BigDecimal("10"),
            null,
            null,
            OffsetDateTime.parse("2026-05-01T00:00:00Z"),
            2L,
            2L);

    var page =
        service.paginate(
            List.of(new RankedItem("A", recommendedKey)), 1, service.encodeCursor(cheapestCursor));

    assertEquals(1, page.items().size());
    assertEquals("A", page.items().getFirst().name());
  }

  @Test
  void decodeCursor_rejectsUnsupportedVersion() {
    AppException exception =
        assertThrows(AppException.class, () -> service.decodeCursor("v2|invalid"));
    assertEquals("INVALID_CURSOR_VERSION", exception.getCode());
  }

  private record RankedItem(String name, FlightRankKey rankKey)
      implements FlightPaginationService.HasRankKey {}
}
