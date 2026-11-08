package dev.baitursinov.caravan.ledger.transaction.service.clearing;

import dev.baitursinov.caravan.ledger.account.entity.AccountRepository;
import dev.baitursinov.caravan.ledger.account.exception.AccountNotFoundException;
import dev.baitursinov.caravan.ledger.account.exception.InsufficientFundsException;
import dev.baitursinov.caravan.ledger.common.ConcurrentModificationRetry;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.entity.TransactionRepository;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.money.CurrencyMismatchException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionClearingService {

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;

  public void clear(TransactionId transactionId) {
    ConcurrentModificationRetry.run(() -> clearLegs(transactionId));
  }

  private void clearLegs(TransactionId transactionId) {
    var transaction = load(transactionId);
    if (transaction.isAborted() || transaction.isCompleted()) {
      return;
    }

    try {
      for (TransactionLeg leg : transaction.legs()) {
        hold(transaction, leg);
      }
    } catch (AccountNotFoundException | InsufficientFundsException | CurrencyMismatchException exception) {
      log.info("Aborting {}: {}", transactionId, exception.getMessage());
      transaction.abort(exception.getMessage());
      transactionRepository.save(transaction);
      return;
    }

    transaction.complete();
    transactionRepository.save(transaction);
  }

  private void hold(Transaction transaction,
                    TransactionLeg leg) {
    var account = accountRepository.findBy(leg.accountId().value())
        .orElseThrow(() -> new AccountNotFoundException(leg.accountId()));
    account.hold(transaction);
    accountRepository.save(account);
  }

  public void rollback(TransactionId transactionId) {
    ConcurrentModificationRetry.run(() -> rollbackLegs(transactionId));
  }

  private void rollbackLegs(TransactionId transactionId) {
    var transaction = load(transactionId);

    // Every leg is rolled back, even those never held, so a hold command replaying can no longer succeed
    for (TransactionLeg leg : transaction.legs()) {
      accountRepository.findBy(leg.accountId().value())
          .filter(account -> !account.hasRolledBackTransaction(transactionId))
          .ifPresent(account -> {
            account.rollback(transaction);
            accountRepository.save(account);
          });
    }
  }

  private Transaction load(TransactionId transactionId) {
    return transactionRepository.findBy(transactionId.value())
        .orElseThrow(() -> new IllegalStateException("transactionId=%s not found".formatted(transactionId)));
  }
}
