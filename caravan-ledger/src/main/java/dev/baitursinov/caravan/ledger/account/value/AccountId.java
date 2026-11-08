package dev.baitursinov.caravan.ledger.account.value;

import dev.baitursinov.caravan.ledger.common.InvalidInputException;

import java.util.UUID;

import static dev.baitursinov.caravan.ledger.common.Utils.hasText;

public record AccountId(String value) {

  public AccountId {
    if (!hasText(value)) {
      throw new InvalidInputException("AccountId must contain text");
    }
  }

  public static AccountId of(String value) {
    return new AccountId(value);
  }

  public static AccountId random() {
    return new AccountId(UUID.randomUUID().toString());
  }

  @Override
  public String toString() {
    return value;
  }
}
