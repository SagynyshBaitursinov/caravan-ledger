package dev.baitursinov.caravan.ledger.common;

import dev.baitursinov.caravan.event.producer.DuplicateEventProductionException;
import dev.baitursinov.caravan.event.sourcing.EventSourcedRepositoryException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentModificationRetryTest {

  private static final EventSourcedRepositoryException CONFLICT = new EventSourcedRepositoryException(
      "Cannot modify one entity in parallel",
      new DuplicateEventProductionException("duplicate"));

  private final AtomicInteger attempts = new AtomicInteger();

  @Test
  void retriesFromTheBeginningUntilTheOperationSucceeds() {
    ConcurrentModificationRetry.run(() -> {
      if (attempts.incrementAndGet() < 3) {
        throw CONFLICT;
      }
    });

    assertThat(attempts).hasValue(3);
  }

  @Test
  void rethrowsTheConflictOnceRetriesAreExhausted() {
    assertThatThrownBy(() -> ConcurrentModificationRetry.run(() -> {
      attempts.incrementAndGet();
      throw CONFLICT;
    })).isSameAs(CONFLICT);

    assertThat(attempts).hasValue(11);
  }

  @Test
  void repositoryFailureOtherThanConflictIsNotRetried() {
    var failure = new EventSourcedRepositoryException("Cannot produce entity events", new RuntimeException());

    assertThatThrownBy(() -> ConcurrentModificationRetry.run(() -> {
      attempts.incrementAndGet();
      throw failure;
    })).isSameAs(failure);

    assertThat(attempts).hasValue(1);
  }

  @Test
  void otherExceptionIsNotRetried() {
    var failure = new IllegalStateException("not found");

    assertThatThrownBy(() -> ConcurrentModificationRetry.run(() -> {
      attempts.incrementAndGet();
      throw failure;
    })).isSameAs(failure);

    assertThat(attempts).hasValue(1);
  }
}
