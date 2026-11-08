package dev.baitursinov.caravan.ledger.transaction.value;

import dev.baitursinov.caravan.ledger.common.InvalidInputException;

import java.util.UUID;

import static dev.baitursinov.caravan.ledger.common.Utils.hasText;

public record TransactionId(String value) {

  public TransactionId {
    if (!hasText(value)) {
      throw new InvalidInputException("TransactionId must contain text");
    }
  }

  public static TransactionId of(String value) {
    return new TransactionId(value);
  }

  public static TransactionId random() {
    return new TransactionId(UUID.randomUUID().toString());
  }

  @Override
  public String toString() {
    return value;
  }
}
