package dev.baitursinov.caravan.ledger.transaction.entity;

import dev.baitursinov.caravan.event.sourcing.EntityName;
import dev.baitursinov.caravan.event.sourcing.EventSourcedEntity;
import dev.baitursinov.caravan.event.sourcing.applying.EventApplier;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionAbortionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionCompletionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionDefinition;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionStatus;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.DEBIT;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.ABORTED;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.COMPLETED;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.STARTED;

@EntityName(Transaction.ENTITY_NAME)
@EventApplier(TransactionEventApplier.class)
public class Transaction extends EventSourcedEntity {

  public static final String ENTITY_NAME = "transaction";

  private static final Comparator<TransactionLeg> DEBITS_FIRST =
      Comparator.<TransactionLeg, Boolean>comparing(leg -> leg.type() != DEBIT)
          .thenComparing(leg -> leg.accountId().value());

  final TransactionId id;

  @Nullable ZonedDateTime startTimestamp;
  List<TransactionLeg> legs = List.of();
  Map<AccountId, TransactionLeg> legsByAccountId = Map.of();
  TransactionStatus status;

  Transaction(TransactionId id) {
    this.id = Objects.requireNonNull(id, "id");
    this.status = TransactionStatus.PENDING;
  }

  public static Transaction start(ZonedDateTime startTimestamp,
                                  List<TransactionLeg> legs) {
    Objects.requireNonNull(startTimestamp, "startTimestamp");
    Objects.requireNonNull(legs, "legs");

    List<TransactionLeg> sortedLegs = legs.stream()
        .sorted(DEBITS_FIRST)
        .toList();

    TransactionValidator.validate(sortedLegs);

    var transaction = new Transaction(TransactionId.random());
    transaction.recordEvent(
        STARTED,
        TransactionDefinition.of(startTimestamp, sortedLegs));
    return transaction;
  }

  public void abort(String reason) {
    if (this.isAborted()) {
      return;
    }
    if (this.isCompleted()) {
      throw new IllegalStateException("%s is already completed".formatted(this));
    }

    recordEvent(ABORTED, new TransactionAbortionDetails(reason));
  }

  public void complete() {
    if (this.isCompleted()) {
      return;
    }
    if (this.isAborted()) {
      throw new IllegalStateException("%s is already aborted".formatted(this));
    }

    recordEvent(COMPLETED, new TransactionCompletionDetails());
  }

  public TransactionStatus status() {
    return status;
  }

  public boolean isAborted() {
    return status == TransactionStatus.ABORTED;
  }

  public boolean isCompleted() {
    return status == TransactionStatus.COMPLETED;
  }

  public ZonedDateTime startTimestamp() {
    if (startTimestamp == null) {
      throw new IllegalStateException("%s is not started".formatted(this));
    }
    return startTimestamp;
  }

  public List<TransactionLeg> legs() {
    return legs;
  }

  public Optional<TransactionLeg> legFor(AccountId accountId) {
    return Optional.ofNullable(legsByAccountId.get(accountId));
  }

  public TransactionId id() {
    return id;
  }

  @Override
  public String entityId() {
    return id.value();
  }
}
