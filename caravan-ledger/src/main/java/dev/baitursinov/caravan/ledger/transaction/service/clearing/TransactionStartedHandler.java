package dev.baitursinov.caravan.ledger.transaction.service.clearing;

import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionDefinition;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.event.Event;
import dev.baitursinov.caravan.event.EventType;
import dev.baitursinov.caravan.event.consumer.handler.EventHandler;
import org.springframework.stereotype.Component;

import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.STARTED;

@Component
public class TransactionStartedHandler implements EventHandler<TransactionDefinition> {

  private static final EventType INTERESTED_EVENT_TYPE = new EventType(Transaction.ENTITY_NAME, STARTED);

  private final TransactionClearingService transactionClearingService;

  public TransactionStartedHandler(TransactionClearingService transactionClearingService) {
    this.transactionClearingService = transactionClearingService;
  }

  @Override
  public boolean isOfInterest(Event<TransactionDefinition> event) {
    return INTERESTED_EVENT_TYPE.equals(event.eventType());
  }

  @Override
  public void handle(Event<TransactionDefinition> event) {
    transactionClearingService.clear(TransactionId.of(event.entityReference().entityId()));
  }
}
