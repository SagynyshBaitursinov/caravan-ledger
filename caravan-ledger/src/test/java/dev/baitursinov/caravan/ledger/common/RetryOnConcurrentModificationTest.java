package dev.baitursinov.caravan.ledger.common;

import dev.baitursinov.caravan.event.producer.DuplicateEventProductionException;
import dev.baitursinov.caravan.event.sourcing.EventSourcedRepositoryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryOnConcurrentModificationTest {

  private static final EventSourcedRepositoryException CONFLICT = new EventSourcedRepositoryException(
      "Cannot modify one entity in parallel",
      new DuplicateEventProductionException("duplicate"));

  private final AnnotationConfigApplicationContext context =
      new AnnotationConfigApplicationContext(RetryConfiguration.class);
  private final Operation operation = context.getBean(Operation.class);
  private final AtomicInteger attempts = new AtomicInteger();

  @AfterEach
  void closeContext() {
    context.close();
  }

  @Test
  void retriesFromTheBeginningUntilTheOperationSucceeds() {
    operation.run(() -> {
      if (attempts.incrementAndGet() < 3) {
        throw CONFLICT;
      }
    });

    assertThat(attempts).hasValue(3);
  }

  @Test
  void rethrowsTheConflictOnceRetriesAreExhausted() {
    assertThatThrownBy(() -> operation.run(() -> {
      attempts.incrementAndGet();
      throw CONFLICT;
    })).isSameAs(CONFLICT);

    assertThat(attempts).hasValue(11);
  }

  @Test
  void repositoryFailureOtherThanConflictIsNotRetried() {
    var failure = new EventSourcedRepositoryException("Cannot produce entity events", new RuntimeException());

    assertThatThrownBy(() -> operation.run(() -> {
      attempts.incrementAndGet();
      throw failure;
    })).isSameAs(failure);

    assertThat(attempts).hasValue(1);
  }

  @Test
  void otherExceptionIsNotRetried() {
    var failure = new IllegalStateException("not found");

    assertThatThrownBy(() -> operation.run(() -> {
      attempts.incrementAndGet();
      throw failure;
    })).isSameAs(failure);

    assertThat(attempts).hasValue(1);
  }

  @Configuration
  @EnableResilientMethods
  static class RetryConfiguration {

    @Bean
    Operation operation() {
      return new Operation();
    }
  }

  static class Operation {

    @RetryOnConcurrentModification
    public void run(Runnable body) {
      body.run();
    }
  }
}
