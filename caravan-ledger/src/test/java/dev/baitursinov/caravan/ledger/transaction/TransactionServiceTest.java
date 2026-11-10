package dev.baitursinov.caravan.ledger.transaction;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.entity.AccountRepository;
import dev.baitursinov.caravan.ledger.account.exception.AccountNotFoundException;
import dev.baitursinov.caravan.ledger.account.service.AccountService;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.transaction.entity.TransactionRepository;
import dev.baitursinov.caravan.ledger.transaction.service.TransactionService;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.joda.money.CurrencyMismatchException;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.baitursinov.caravan.ledger.account.entity.Account.DEFAULT_CURRENCY;
import static dev.baitursinov.caravan.ledger.common.value.Direction.CREDIT;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joda.money.CurrencyUnit.EUR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

  private static final Money FIVE = Money.of(DEFAULT_CURRENCY, 5);

  private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final TransactionService service =
      new TransactionService(transactionRepository, new AccountService(accountRepository));

  private final Account from = Account.open(true);

  @Test
  void transactionOnMissingAccountIsNotStarted() {
    var missingAccountId = AccountId.random();
    when(accountRepository.findBy(from.id().value())).thenReturn(Optional.of(from));

    assertThatThrownBy(() -> service.start(List.of(
        TransactionLeg.of(from.id(), DEBIT, FIVE),
        TransactionLeg.of(missingAccountId, CREDIT, FIVE))))
        .isInstanceOf(AccountNotFoundException.class);
    verify(transactionRepository, never()).save(any());
  }

  @Test
  void transactionInOtherCurrencyThanAccountIsNotStarted() {
    var euroAccount = Account.open(false, EUR);
    when(accountRepository.findBy(from.id().value())).thenReturn(Optional.of(from));
    when(accountRepository.findBy(euroAccount.id().value())).thenReturn(Optional.of(euroAccount));

    assertThatThrownBy(() -> service.start(List.of(
        TransactionLeg.of(from.id(), DEBIT, FIVE),
        TransactionLeg.of(euroAccount.id(), CREDIT, FIVE))))
        .isInstanceOf(CurrencyMismatchException.class);
    verify(transactionRepository, never()).save(any());
  }
}
