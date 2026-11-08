package dev.baitursinov.caravan.ledger.account.service;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.entity.AccountRepository;
import dev.baitursinov.caravan.ledger.account.exception.AccountNotFoundException;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import lombok.RequiredArgsConstructor;
import org.joda.money.CurrencyUnit;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;

  public Account open(boolean overdraftAllowed,
                      CurrencyUnit currency) {
    var account = Account.open(overdraftAllowed, currency);
    accountRepository.save(account);
    return account;
  }

  public Account get(AccountId accountId) {
    return accountRepository.findBy(accountId.value())
        .orElseThrow(() -> new AccountNotFoundException(accountId));
  }
}
