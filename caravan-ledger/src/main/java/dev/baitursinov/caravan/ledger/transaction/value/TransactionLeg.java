package dev.baitursinov.caravan.ledger.transaction.value;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.common.InvalidInputException;
import dev.baitursinov.caravan.ledger.common.value.Direction;
import org.joda.money.Money;

public record TransactionLeg(AccountId accountId,
                             Direction direction,
                             Money amount) {

  public TransactionLeg {
    if (!amount.isPositive()) {
      throw new InvalidInputException("amount must be positive, got " + amount);
    }
  }

  public static TransactionLeg of(AccountId accountId,
                                  Direction direction,
                                  Money amount) {
    return new TransactionLeg(accountId, direction, amount);
  }
}
