package dev.baitursinov.caravan.ledger.account.exception;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import org.joda.money.Money;

public class InsufficientFundsException extends RuntimeException {

  public InsufficientFundsException(AccountId accountId,
                                    Money balance,
                                    Money requested) {
    super("%s has balance %s, cannot debit %s without overdraft"
        .formatted(accountId, balance, requested));
  }
}
