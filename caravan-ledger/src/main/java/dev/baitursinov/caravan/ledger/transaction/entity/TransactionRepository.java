package dev.baitursinov.caravan.ledger.transaction.entity;

import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.event.sourcing.EventSourcedRepository;
import dev.baitursinov.caravan.event.sourcing.EventSourcingRepositoryContext;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository extends EventSourcedRepository<Transaction> {

  public TransactionRepository(EventSourcingRepositoryContext context) {
    super(Transaction.class, context);
  }

  @Override
  protected Transaction createWithBlankState(String entityId) {
    return new Transaction(TransactionId.of(entityId));
  }
}
