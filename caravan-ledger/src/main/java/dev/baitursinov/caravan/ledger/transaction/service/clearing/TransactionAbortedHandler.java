package dev.baitursinov.caravan.ledger.transaction.service.clearing;

import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionAbortionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.event.Event;
import dev.baitursinov.caravan.event.EventType;
import dev.baitursinov.caravan.event.consumer.handler.EventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.ABORTED;

@Component
@RequiredArgsConstructor
public class TransactionAbortedHandler implements EventHandler<TransactionAbortionDetails> {

  private static final EventType INTERESTED_EVENT_TYPE = new EventType(Transaction.ENTITY_NAME, ABORTED);

  private final TransactionClearingService transactionClearingService;

  @Override
  public boolean isOfInterest(Event<TransactionAbortionDetails> event) {
    return INTERESTED_EVENT_TYPE.equals(event.eventType());
  }

  @Override
  public void handle(Event<TransactionAbortionDetails> event) {
    transactionClearingService.rollback(TransactionId.of(event.entityReference().entityId()));
  }
}
