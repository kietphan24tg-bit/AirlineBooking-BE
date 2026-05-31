package com.example.appbootstrap.application.booking;

import com.example.common.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserTripHistoryPaginationService {

  private static final String CURSOR_VERSION = "v1";
  private static final String CURSOR_NULL_DEPARTURE = "";

  public <T extends HasRankKey> PageResult<T> paginate(List<T> sortedItems, int limit, String cursor) {
    List<T> sorted = sortedItems.stream().sorted(Comparator.comparing(HasRankKey::rankKey)).toList();
    UserTripHistoryRankKey cursorKey = decodeCursor(cursor);

    List<T> pageItems = collectPage(sorted, limit, cursorKey);
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

  public String encodeCursor(UserTripHistoryRankKey key) {
    String departurePart =
        key.departureTime() == null ? CURSOR_NULL_DEPARTURE : key.departureTime().toString();
    String payload =
        key.status().name()
            + "|"
            + key.direction().name()
            + "|"
            + departurePart
            + "|"
            + key.bookingId()
            + "|"
            + key.flightId();
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return CURSOR_VERSION + "|" + encoded;
  }

  public UserTripHistoryRankKey decodeCursor(String cursor) {
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
      return new UserTripHistoryRankKey(
          UserTripHistoryService.TripFilterStatus.valueOf(parts[0]),
          SortDirection.valueOf(parts[1]),
          parts[2].isEmpty() ? null : OffsetDateTime.parse(parts[2]),
          Long.parseLong(parts[3]),
          Long.parseLong(parts[4]));
    } catch (IllegalArgumentException exception) {
      throw new AppException("INVALID_CURSOR_FORMAT", "Cursor payload is invalid", 400);
    }
  }

  private <T extends HasRankKey> List<T> collectPage(
      List<T> sortedItems, int limit, UserTripHistoryRankKey cursorKey) {
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
    UserTripHistoryRankKey rankKey();
  }

  public enum SortDirection {
    ASC,
    DESC
  }

  public record UserTripHistoryRankKey(
      UserTripHistoryService.TripFilterStatus status,
      SortDirection direction,
      OffsetDateTime departureTime,
      long bookingId,
      long flightId)
      implements Comparable<UserTripHistoryRankKey> {

    @Override
    public int compareTo(UserTripHistoryRankKey other) {
      int byStatus = status.compareTo(other.status);
      if (byStatus != 0) {
        return byStatus;
      }
      int byDirection = direction.compareTo(other.direction);
      if (byDirection != 0) {
        return byDirection;
      }
      int byDeparture = compareDeparture(departureTime, other.departureTime, direction);
      if (byDeparture != 0) {
        return byDeparture;
      }
      int byBookingId = Long.compare(bookingId, other.bookingId);
      if (byBookingId != 0) {
        return byBookingId;
      }
      return Long.compare(flightId, other.flightId);
    }

    private static int compareDeparture(
        OffsetDateTime left, OffsetDateTime right, SortDirection direction) {
      if (left == null && right == null) {
        return 0;
      }
      if (left == null) {
        return 1;
      }
      if (right == null) {
        return -1;
      }
      return direction == SortDirection.ASC ? left.compareTo(right) : right.compareTo(left);
    }
  }

  public record PageResult<T>(List<T> items, String nextCursor, boolean hasMore, int limit) {}
}
