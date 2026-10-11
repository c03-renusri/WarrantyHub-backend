package com.warrantyhub.vault;

import java.time.*;
import java.time.temporal.ChronoUnit;

public final class WarrantyDates {
  private WarrantyDates() {}

  public static LocalDate expiry(LocalDate purchase, int months) {
    return purchase.plusMonths(months);
  }

  public static long days(LocalDate expiry, LocalDate today) {
    return ChronoUnit.DAYS.between(today, expiry);
  }

  public static String status(LocalDate expiry, LocalDate today) {
    long d = days(expiry, today);
    return d < 0 ? "EXPIRED" : d <= 30 ? "EXPIRING" : "ACTIVE";
  }
}
