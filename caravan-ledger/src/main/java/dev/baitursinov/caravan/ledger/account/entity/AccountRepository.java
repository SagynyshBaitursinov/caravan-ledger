package dev.baitursinov.caravan.ledger.account.entity;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.event.sourcing.EventSourcedRepository;
import dev.baitursinov.caravan.event.sourcing.EventSourcingRepositoryContext;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository extends EventSourcedRepository<Account> {

  public AccountRepository(EventSourcingRepositoryContext context) {
    super(Account.class, context);
  }

  @Override
  protected Account createWithBlankState(String entityId) {
    return new Account(AccountId.of(entityId));
  }
}
