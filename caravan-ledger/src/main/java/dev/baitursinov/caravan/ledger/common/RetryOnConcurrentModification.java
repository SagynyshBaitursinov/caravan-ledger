package dev.baitursinov.caravan.ledger.common;

import org.springframework.resilience.annotation.Retryable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Retries an idempotent method from the beginning when another writer saved the same entity first.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retryable(
    predicate = ConcurrentModificationPredicate.class,
    maxRetries = 10,
    delay = 20,
    jitter = 20,
    multiplier = 2,
    maxDelay = 200)
public @interface RetryOnConcurrentModification {
}
