package dev.baitursinov.caravan.ledger.transaction.entity;

import dev.baitursinov.caravan.event.sourcing.EntityName;
import dev.baitursinov.caravan.event.sourcing.EventSourcedEntity;
import dev.baitursinov.caravan.event.sourcing.applying.EventApplier;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.common.value.Direction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionAbortionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionCompletionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionDefinition;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionStatus;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static dev.baitursinov.caravan.ledger.common.value.Direction.CREDIT;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.ABORTED;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.COMPLETED;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.STARTED;

@EntityName(Transaction.ENTITY_NAME)
@EventApplier(TransactionEventApplier.class)
public class Transaction extends EventSourcedEntity {

  public static final String ENTITY_NAME = "transaction";

  final TransactionId id;

  @Nullable ZonedDateTime startTimestamp;
  List<TransactionLeg> legs = List.of();
  Map<AccountId, TransactionLeg> legsByAccountId = Map.of();
  TransactionStatus status;
  @Nullable String abortedReason;

  Transaction(TransactionId id) {
    this.id = Objects.requireNonNull(id, "id");
    this.status = TransactionStatus.PENDING;
  }

  public static Transaction start(ZonedDateTime startTimestamp,
                                  List<TransactionLeg> legs) {
    Objects.requireNonNull(startTimestamp, "startTimestamp");
    Objects.requireNonNull(legs, "legs");

    TransactionValidator.validate(legs);

    var transaction = new Transaction(TransactionId.random());
    transaction.recordEvent(
        STARTED,
        TransactionDefinition.of(startTimestamp, legs));
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

  public @Nullable String abortedReason() {
    return abortedReason;
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

  public List<TransactionLeg> debitLegs() {
    return legsOf(DEBIT);
  }

  public List<TransactionLeg> creditLegs() {
    return legsOf(CREDIT);
  }

  private List<TransactionLeg> legsOf(Direction direction) {
    return legs.stream()
        .filter(leg -> leg.direction() == direction)
        .toList();
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
