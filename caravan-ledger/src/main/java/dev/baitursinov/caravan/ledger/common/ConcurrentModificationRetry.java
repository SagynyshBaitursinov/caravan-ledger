package dev.baitursinov.caravan.ledger.common;

import dev.baitursinov.caravan.event.producer.DuplicateEventProductionException;
import dev.baitursinov.caravan.event.sourcing.EventSourcedRepositoryException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConcurrentModificationRetry {

  private static final RetryTemplate RETRY_TEMPLATE = new RetryTemplate(
      RetryPolicy.builder()
          .predicate(ConcurrentModificationRetry::isConcurrentModification)
          .maxRetries(10)
          .delay(Duration.ofMillis(20))
          .jitter(Duration.ofMillis(20))
          .multiplier(2)
          .maxDelay(Duration.ofMillis(200))
          .build());

  public static void run(Runnable idempotentOperation) {
    RETRY_TEMPLATE.invoke(idempotentOperation);
  }

  private static boolean isConcurrentModification(Throwable throwable) {
    return throwable instanceof EventSourcedRepositoryException
        && throwable.getCause() instanceof DuplicateEventProductionException;
  }
}
