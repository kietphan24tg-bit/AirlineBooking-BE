package com.example.moduleflight.application;

import com.example.common.exception.AppException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FlightPaginationService {

  private static final String CURSOR_VERSION = "v1";
  private static final String CURSOR_NULL_DECIMAL = "";
  private static final String CURSOR_NULL_DEPARTURE = "";

  public <T extends HasRankKey> PageResult<T> paginate(
      List<T> sortedItems, int limit, String cursor) {
    List<T> sorted =
        sortedItems.stream().sorted(Comparator.comparing(HasRankKey::rankKey)).toList();
    FlightRankKey cursorKey = decodeCursor(cursor);
    if (cursorKey != null
        && !sorted.isEmpty()
        && cursorKey.sortBy() != sorted.getFirst().rankKey().sortBy()) {
      cursorKey = null;
    }

    List<T> pageItems = collectPage(sorted, limit, cursorKey);
    // If the result set changed because the client switched sort/filter but still sent an older
    // cursor, restart from the first page instead of returning an empty list.
    if (cursorKey != null && pageItems.isEmpty() && !sorted.isEmpty()) {
      pageItems = collectPage(sorted, limit, null);
    }

    boolean hasMore = pageItems.size() > limit;
    if (hasMore) {
      pageItems.removeLast();
    }

    String nextCursor =
        hasMore && !pageItems.isEmpty() ? encodeCursor(pageItems.getLast().rankKey()) : null;
    return new PageResult<>(pageItems, nextCursor, hasMore, limit);
  }

  public String encodeCursor(FlightRankKey key) {
    String primaryPart =
        key.primaryMetric() == null ? CURSOR_NULL_DECIMAL : key.primaryMetric().toPlainString();
    String secondaryPart =
        key.secondaryMetric() == null ? CURSOR_NULL_DECIMAL : key.secondaryMetric().toPlainString();
    String tertiaryPart =
        key.tertiaryMetric() == null ? CURSOR_NULL_DECIMAL : key.tertiaryMetric().toPlainString();
    String departurePart =
        key.departureTime() == null ? CURSOR_NULL_DEPARTURE : key.departureTime().toString();
    String payload =
        key.sortBy().name()
            + "|"
            + primaryPart
            + "|"
            + secondaryPart
            + "|"
            + tertiaryPart
            + "|"
            + departurePart
            + "|"
            + key.flightId()
            + "|"
            + key.fareId();
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return CURSOR_VERSION + "|" + encoded;
  }

  public FlightRankKey decodeCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }
    String[] versioned = cursor.split("\\|", 2);
    if (versioned.length != 2) {
      throw new AppException("INVALID_CURSOR_FORMAT", "Cursor format is invalid", 400);
    }
    if (!CURSOR_VERSION.equalsIgnoreCase(versioned[0])) {
      throw new AppException("INVALID_CURSOR_VERSION", "Cursor version is not supported", 400);
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(versioned[1]), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|", -1);
      if (parts.length != 7) {
        throw new AppException("INVALID_CURSOR_FORMAT", "Cursor payload is invalid", 400);
      }
      return new FlightRankKey(
          FlightSortBy.valueOf(parts[0]),
          parseDecimal(parts[1]),
          parseDecimal(parts[2]),
          parseDecimal(parts[3]),
          parseDeparture(parts[4]),
          Long.parseLong(parts[5]),
          Long.parseLong(parts[6]));
    } catch (IllegalArgumentException exception) {
      throw new AppException("INVALID_CURSOR_FORMAT", "Cursor payload is invalid", 400);
    }
  }

  private BigDecimal parseDecimal(String value) {
    return value.isEmpty() ? null : new BigDecimal(value);
  }

  private OffsetDateTime parseDeparture(String value) {
    return value.isEmpty() ? null : OffsetDateTime.parse(value);
  }

  private <T extends HasRankKey> List<T> collectPage(
      List<T> sortedItems, int limit, FlightRankKey cursorKey) {
    List<T> pageItems = new ArrayList<>();
    for (T item : sortedItems) {
      if (cursorKey != null && item.rankKey().compareTo(cursorKey) <= 0) {
        continue;
      }
      pageItems.add(item);
      if (pageItems.size() == limit + 1) {
        break;
      }
    }
    return pageItems;
  }

  public interface HasRankKey {
    FlightRankKey rankKey();
  }

  public record PageResult<T>(List<T> items, String nextCursor, boolean hasMore, int limit) {}
}
