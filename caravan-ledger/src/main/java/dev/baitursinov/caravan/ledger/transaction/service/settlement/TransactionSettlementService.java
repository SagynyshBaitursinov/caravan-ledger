package dev.baitursinov.caravan.ledger.transaction.service.settlement;

import dev.baitursinov.caravan.ledger.account.service.AccountService;
import dev.baitursinov.caravan.ledger.transaction.service.TransactionService;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionSettlementService {

  private final TransactionService transactionService;
  private final AccountService accountService;

  public void settle(TransactionId transactionId) {
    var transaction = transactionService.get(transactionId);

    for (TransactionLeg leg : transaction.legs()) {
      accountService.settle(leg.accountId(), transaction);
    }
  }
}
