package com.example.modulepayment.exception;

import com.example.common.exception.AppException;
import java.util.Map;

public class PaymentNotFoundException extends AppException {

  public PaymentNotFoundException(Long paymentId) {
    super(
        "PAYMENT_NOT_FOUND",
        "Payment not found",
        404,
        Map.of("paymentId", paymentId));
  }

  public PaymentNotFoundException(String transactionId) {
    super(
        "PAYMENT_NOT_FOUND",
        "Payment not found",
        404,
        Map.of("transactionId", transactionId));
  }
}
