package com.example.modulepayment.exception;

import com.example.common.exception.AppException;

public class PaymentExecutionUnknownException extends AppException {

  public PaymentExecutionUnknownException() {
    super(
        "PAYMENT_EXECUTION_UNKNOWN",
        "Payment provider result is unknown; check the existing payment before retrying",
        202);
  }
}
