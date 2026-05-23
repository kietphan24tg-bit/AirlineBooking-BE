package com.example.modulepayment.application;

import java.time.OffsetDateTime;

public record ActivePayLaterSessionView(Long paymentId, OffsetDateTime expireAt) {}
