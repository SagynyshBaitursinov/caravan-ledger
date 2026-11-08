package dev.baitursinov.caravan.ledger.transaction.entity;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionAbortionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionCompletionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionDefinition;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionStatus;
import dev.baitursinov.caravan.event.Event;
import dev.baitursinov.caravan.event.sourcing.applying.ApplyEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.ABORTED;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.COMPLETED;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.STARTED;

final class TransactionEventApplier {

  @ApplyEvent(STARTED)
  static void applyTransactionStarted(Transaction transaction,
                                      Event<TransactionDefinition> transactionStartedEvent) {
    var transactionDefinition = transactionStartedEvent.payload();
    transaction.startTimestamp = transactionDefinition.transactionStartTimestamp();
    transaction.legs = transactionDefinition.legs();
    transaction.legsByAccountId = createLegsByAccountId(transactionDefinition);
  }

  @ApplyEvent(ABORTED)
  static void applyTransactionAborted(Transaction transaction,
                                      Event<TransactionAbortionDetails> event) {
    transaction.status = TransactionStatus.ABORTED;
    transaction.abortedReason = event.payload().reason();
  }

  @ApplyEvent(COMPLETED)
  static void applyTransactionCompleted(Transaction transaction,
                                        Event<TransactionCompletionDetails> event) {
    transaction.status = TransactionStatus.COMPLETED;
  }

  private static Map<AccountId, TransactionLeg> createLegsByAccountId(TransactionDefinition started) {
    Map<AccountId, TransactionLeg> legsByAccountId = new LinkedHashMap<>();
    started.legs()
        .forEach(leg ->
            legsByAccountId.put(leg.accountId(), leg));
    return Collections.unmodifiableMap(legsByAccountId);
  }
}
