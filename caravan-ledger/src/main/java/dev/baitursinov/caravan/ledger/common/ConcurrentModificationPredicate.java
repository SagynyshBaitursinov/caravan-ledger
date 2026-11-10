package dev.baitursinov.caravan.ledger.common;

import dev.baitursinov.caravan.event.producer.DuplicateEventProductionException;
import dev.baitursinov.caravan.event.sourcing.EventSourcedRepositoryException;
import org.springframework.resilience.retry.MethodRetryPredicate;

import java.lang.reflect.Method;

public class ConcurrentModificationPredicate implements MethodRetryPredicate {

  @Override
  public boolean shouldRetry(Method method, Throwable throwable) {
    return throwable instanceof EventSourcedRepositoryException
        && throwable.getCause() instanceof DuplicateEventProductionException;
  }
}
