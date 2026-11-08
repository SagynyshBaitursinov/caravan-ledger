package dev.baitursinov.caravan.ledger.transaction.service.settlement;

import dev.baitursinov.caravan.ledger.account.entity.AccountRepository;
import dev.baitursinov.caravan.ledger.common.ConcurrentModificationRetry;
import dev.baitursinov.caravan.ledger.transaction.entity.TransactionRepository;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionSettlementService {

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;

  public void settle(TransactionId transactionId) {
    ConcurrentModificationRetry.run(() -> settleLegs(transactionId));
  }

  private void settleLegs(TransactionId transactionId) {
    var transaction = transactionRepository.findBy(transactionId.value())
        .orElseThrow(() -> new IllegalStateException("transactionId=%s not found".formatted(transactionId)));
    if (!transaction.isCompleted()) {
      return;
    }

    for (TransactionLeg leg : transaction.legs()) {
      var account = accountRepository.findBy(leg.accountId().value())
          .orElseThrow(() -> new IllegalStateException("accountId=%s not found".formatted(leg.accountId())));
      if (account.hasSettledTransaction(transactionId)) {
        continue;
      }
      account.settle(transaction);
      accountRepository.save(account);
    }
  }
}
