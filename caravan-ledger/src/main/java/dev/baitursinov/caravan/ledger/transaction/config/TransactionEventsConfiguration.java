package dev.baitursinov.caravan.ledger.transaction.config;

import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionAbortionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionCompletionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionDefinition;
import dev.baitursinov.caravan.event.EntityEventsRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class TransactionEventsConfiguration {

  public static final String STARTED = "started";
  public static final String COMPLETED = "completed";
  public static final String ABORTED = "aborted";

  @Bean
  public EntityEventsRegistration transactionEventsRegistration() {
    return new EntityEventsRegistration(
        Transaction.ENTITY_NAME,
        Map.of(
            STARTED, TransactionDefinition.class,
            ABORTED, TransactionAbortionDetails.class,
            COMPLETED, TransactionCompletionDetails.class));
  }
}
