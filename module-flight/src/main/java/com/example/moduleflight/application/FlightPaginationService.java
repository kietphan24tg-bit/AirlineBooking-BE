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

  public <T extends HasRankKey> PageResult<T> paginate(List<T> sortedItems, int limit, String cursor) {
    List<T> sorted = sortedItems.stream().sorted(Comparator.comparing(HasRankKey::rankKey)).toList();
    FlightRankKey cursorKey = decodeCursor(cursor);

    List<T> pageItems = new ArrayList<>();
    for (T item : sorted) {
      if (cursorKey != null && item.rankKey().compareTo(cursorKey) <= 0) {
        continue;
      }
      pageItems.add(item);
      if (pageItems.size() == limit + 1) {
        break;
      }
    }

    boolean hasMore = pageItems.size() > limit;
    if (hasMore) {
      pageItems.removeLast();
    }

    String nextCursor = hasMore && !pageItems.isEmpty() ? encodeCursor(pageItems.getLast().rankKey()) : null;
    return new PageResult<>(pageItems, nextCursor, hasMore, limit);
  }

  public String encodeCursor(FlightRankKey key) {
    String payload =
        key.promoRank()
            + "|"
            + key.effectivePrice().toPlainString()
            + "|"
            + key.departureTime()
            + "|"
            + key.flightId()
            + "|"
            + key.fareId();
    String encoded =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
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
      if (parts.length != 5) {
        throw new AppException("INVALID_CURSOR_FORMAT", "Cursor payload is invalid", 400);
      }
      return new FlightRankKey(
          Integer.parseInt(parts[0]),
          new BigDecimal(parts[1]),
          OffsetDateTime.parse(parts[2]),
          Long.parseLong(parts[3]),
          Long.parseLong(parts[4]));
    } catch (IllegalArgumentException exception) {
      throw new AppException("INVALID_CURSOR_FORMAT", "Cursor payload is invalid", 400);
    }
  }

  public interface HasRankKey {
    FlightRankKey rankKey();
  }

  public record PageResult<T>(List<T> items, String nextCursor, boolean hasMore, int limit) {}
}
