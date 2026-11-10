package dev.baitursinov.caravan.ledger.account.value;

import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import org.joda.money.Money;

public record ReservationDetails(TransactionId transactionId,
                                 Money amount) {

  public ReservationDetails {
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("amount must be positive, got " + amount);
    }
  }
}
