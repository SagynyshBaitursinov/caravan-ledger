package dev.baitursinov.caravan.ledger.transaction.service;

import dev.baitursinov.caravan.ledger.account.service.AccountService;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.entity.TransactionRepository;
import dev.baitursinov.caravan.ledger.transaction.exception.TransactionNotFoundException;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final AccountService accountService;

  public Transaction get(TransactionId transactionId) {
    return transactionRepository.findBy(transactionId.value())
        .orElseThrow(() -> new TransactionNotFoundException(transactionId));
  }

  public Transaction start(List<TransactionLeg> legs) {
    var transaction = Transaction.start(ZonedDateTime.now(), legs);
    for (TransactionLeg leg : legs) {
      accountService.get(leg.accountId()).validateCurrency(leg);
    }
    transactionRepository.save(transaction);
    return transaction;
  }

  public void complete(Transaction transaction) {
    transaction.complete();
    transactionRepository.save(transaction);
  }

  public void abort(Transaction transaction, String reason) {
    transaction.abort(reason);
    transactionRepository.save(transaction);
  }
}
