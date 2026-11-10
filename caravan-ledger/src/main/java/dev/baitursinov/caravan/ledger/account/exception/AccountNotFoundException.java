package dev.baitursinov.caravan.ledger.account.exception;

import dev.baitursinov.caravan.ledger.account.value.AccountId;

public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException(AccountId accountId) {
    super("accountId=%s does not exist".formatted(accountId));
  }
}
