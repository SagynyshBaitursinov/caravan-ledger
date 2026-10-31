package dev.baitursinov.caravan.ledger.account.value;

import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import lombok.Builder;
import org.joda.money.Money;

import java.time.ZonedDateTime;

@Builder
public record LedgerEntry(AccountId accountId,
                          TransactionId transactionId,
                          ZonedDateTime timestamp,
                          LedgerEntryType type,
                          Money amount) {

  public LedgerEntry {
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("amount must be positive, got " + amount);
    }
  }
}
