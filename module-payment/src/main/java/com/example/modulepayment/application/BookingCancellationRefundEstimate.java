package com.example.modulepayment.application;

import org.springframework.util.StringUtils;

public record BookingCancellationRefundEstimate(
    String title, String message, String note) {

  private static final String PROVIDER_STRIPE = "STRIPE";
  private static final String PROVIDER_PAYOS = "PAYOS";

  public static BookingCancellationRefundEstimate forProvider(String provider) {
    String normalized = normalize(provider);
    return switch (normalized) {
      case PROVIDER_STRIPE ->
          new BookingCancellationRefundEstimate(
              "Thời gian hoàn tiền dự kiến",
              "Dự kiến tiền hoàn sẽ về trong vòng 5-10 ngày làm việc.",
              "Thời gian thực tế có thể thay đổi tùy theo ngân hàng phát hành thẻ.");
      case PROVIDER_PAYOS ->
          new BookingCancellationRefundEstimate(
              "Trạng thái hoàn tiền",
              "Lệnh hoàn tiền đã được tiếp nhận và xử lý 24/7.",
              "Thời gian nhận tiền thực tế phụ thuộc vào ngân hàng nhận.");
      default ->
          new BookingCancellationRefundEstimate(
              "Trạng thái hoàn tiền",
              "Yêu cầu hoàn tiền của bạn đã được tiếp nhận thành công.",
              "Thời gian nhận tiền thực tế phụ thuộc vào đơn vị thanh toán và ngân hàng xử lý.");
    };
  }

  private static String normalize(String provider) {
    if (!StringUtils.hasText(provider)) {
      return "";
    }
    return provider.trim().toUpperCase();
  }
}
