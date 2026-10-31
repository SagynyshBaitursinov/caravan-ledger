package dev.baitursinov.caravan.ledger.transaction.value;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.account.value.LedgerEntryType;
import org.joda.money.Money;

public record TransactionLeg(AccountId accountId,
                             LedgerEntryType type,
                             Money amount) {

  public TransactionLeg {
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("amount must be positive, got " + amount);
    }
  }

  public static TransactionLeg of(AccountId accountId,
                                  LedgerEntryType type,
                                  Money amount) {
    return new TransactionLeg(accountId, type, amount);
  }
}
