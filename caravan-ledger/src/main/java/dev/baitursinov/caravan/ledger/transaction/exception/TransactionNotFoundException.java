package dev.baitursinov.caravan.ledger.transaction.exception;

import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;

public class TransactionNotFoundException extends RuntimeException {

  public TransactionNotFoundException(TransactionId transactionId) {
    super("%s does not exist".formatted(transactionId));
  }
}
