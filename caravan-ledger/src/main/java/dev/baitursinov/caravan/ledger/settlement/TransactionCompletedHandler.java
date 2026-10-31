package dev.baitursinov.caravan.ledger.settlement;

import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionCompletionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.event.Event;
import dev.baitursinov.caravan.event.EventType;
import dev.baitursinov.caravan.event.consumer.handler.EventHandler;
import org.springframework.stereotype.Component;

import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.COMPLETED;

@Component
public class TransactionCompletedHandler implements EventHandler<TransactionCompletionDetails> {

  private static final EventType INTERESTED_EVENT_TYPE = new EventType(Transaction.ENTITY_NAME, COMPLETED);

  private final SettlementService settlementService;

  public TransactionCompletedHandler(SettlementService settlementService) {
    this.settlementService = settlementService;
  }

  @Override
  public boolean isOfInterest(Event<TransactionCompletionDetails> event) {
    return INTERESTED_EVENT_TYPE.equals(event.eventType());
  }

  @Override
  public void handle(Event<TransactionCompletionDetails> event) {
    settlementService.settle(TransactionId.of(event.entityReference().entityId()));
  }
}
