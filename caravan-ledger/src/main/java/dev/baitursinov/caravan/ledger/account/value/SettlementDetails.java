package dev.baitursinov.caravan.ledger.account.value;

import dev.baitursinov.caravan.ledger.common.value.Direction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import lombok.Builder;
import org.joda.money.Money;

import java.time.ZonedDateTime;

@Builder
public record SettlementDetails(TransactionId transactionId,
                                ZonedDateTime timestamp,
                                Direction direction,
                                Money amount) {

  public SettlementDetails {
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("amount must be positive, got " + amount);
    }
  }
}
