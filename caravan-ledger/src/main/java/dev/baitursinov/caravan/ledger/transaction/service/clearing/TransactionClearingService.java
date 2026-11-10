package dev.baitursinov.caravan.ledger.transaction.service.clearing;

import dev.baitursinov.caravan.ledger.account.service.AccountService;
import dev.baitursinov.caravan.ledger.transaction.service.TransactionService;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionClearingService {

  private final TransactionService transactionService;
  private final AccountService accountService;

  public void clear(TransactionId transactionId) {
    var transaction = transactionService.get(transactionId);
    if (transaction.isAborted() || transaction.isCompleted()) {
      return;
    }

    for (TransactionLeg leg : transaction.debitLegs()) {
      boolean isReserved = accountService.reserve(leg.accountId(), transaction);
      if (!isReserved) {
        transactionService.abort(
            transaction,
            "accountId=%s rejected reservation of balance".formatted(leg.accountId()));
        return;
      }
    }

    transactionService.complete(transaction);
  }

  public void rollback(TransactionId transactionId) {
    var transaction = transactionService.get(transactionId);

    // Every debit is rolled back, even those never reserved, so a reserve command replaying can no longer succeed.
    // Credits reserve nothing, so there is nothing to release
    for (TransactionLeg leg : transaction.debitLegs()) {
      accountService.rollback(leg.accountId(), transaction);
    }
  }
}
