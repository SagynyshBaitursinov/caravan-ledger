package dev.baitursinov.caravan.ledger.transaction.value;

import java.time.ZonedDateTime;
import java.util.List;

public record TransactionDefinition(ZonedDateTime transactionStartTimestamp,
                                    List<TransactionLeg> legs) {

  public TransactionDefinition {
    legs = List.copyOf(legs);
  }

  public static TransactionDefinition of(ZonedDateTime transactionStartTimestamp,
                                         List<TransactionLeg> legs) {
    return new TransactionDefinition(transactionStartTimestamp, legs);
  }
}
