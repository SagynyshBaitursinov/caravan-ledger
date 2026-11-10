package dev.baitursinov.caravan.ledger.account.service;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.entity.AccountRepository;
import dev.baitursinov.caravan.ledger.account.exception.AccountNotFoundException;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.common.RetryOnConcurrentModification;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.joda.money.CurrencyUnit;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;

  public Account get(AccountId accountId) {
    return accountRepository.findBy(accountId.value())
        .orElseThrow(() -> new AccountNotFoundException(accountId));
  }

  public Account open(boolean overdraftAllowed,
                      CurrencyUnit currency) {
    var account = Account.open(overdraftAllowed, currency);
    accountRepository.save(account);
    return account;
  }

  @RetryOnConcurrentModification
  public boolean reserve(AccountId accountId, Transaction transaction) {
    var account = get(accountId);
    account.reserve(transaction);
    accountRepository.save(account);
    return account.hasReservedTransaction(transaction.id());
  }

  @RetryOnConcurrentModification
  public void settle(AccountId accountId, Transaction transaction) {
    var account = get(accountId);
    if (account.hasSettledTransaction(transaction.id())) {
      return;
    }
    account.settle(transaction);
    accountRepository.save(account);
  }

  @RetryOnConcurrentModification
  public void rollback(AccountId accountId, Transaction transaction) {
    var account = get(accountId);
    if (account.hasRolledBackTransaction(transaction.id())) {
      return;
    }
    account.rollback(transaction);
    accountRepository.save(account);
  }
}
