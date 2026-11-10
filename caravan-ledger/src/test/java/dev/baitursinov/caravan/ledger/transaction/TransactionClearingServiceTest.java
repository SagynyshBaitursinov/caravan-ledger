package dev.baitursinov.caravan.ledger.transaction;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.entity.AccountRepository;
import dev.baitursinov.caravan.ledger.account.exception.AccountNotFoundException;
import dev.baitursinov.caravan.ledger.account.service.AccountService;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.entity.TransactionRepository;
import dev.baitursinov.caravan.ledger.transaction.service.TransactionService;
import dev.baitursinov.caravan.ledger.transaction.service.clearing.TransactionClearingService;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static dev.baitursinov.caravan.ledger.account.entity.Account.DEFAULT_CURRENCY;
import static dev.baitursinov.caravan.ledger.common.value.Direction.CREDIT;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionClearingServiceTest {

  private static final Money FIVE = Money.of(DEFAULT_CURRENCY, 5);

  private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final AccountService accountService = new AccountService(accountRepository);
  private final TransactionClearingService service = new TransactionClearingService(
      new TransactionService(transactionRepository, accountService),
      accountService);

  private final Account from = Account.open(false);
  private final Transaction transaction = Transaction.start(
      ZonedDateTime.now(),
      List.of(
          TransactionLeg.of(from.id(), DEBIT, FIVE),
          TransactionLeg.of(AccountId.random(), CREDIT, FIVE)));

  @Test
  void rejectionIsSavedOnAccountBeforeAborting() {
    when(transactionRepository.findBy(transaction.id().value())).thenReturn(Optional.of(transaction));
    when(accountRepository.findBy(from.id().value())).thenReturn(Optional.of(from));

    service.clear(transaction.id());

    var inOrder = inOrder(accountRepository, transactionRepository);
    inOrder.verify(accountRepository).save(from);
    inOrder.verify(transactionRepository).save(transaction);
    assertThat(from.hasRejectedTransaction(transaction.id())).isTrue();
    assertThat(transaction.isAborted()).isTrue();
    assertThat(transaction.abortedReason()).contains("rejected reservation of balance");
  }

  @Test
  void accountNotVisibleYetFailsClearingWithoutAborting() {
    when(transactionRepository.findBy(transaction.id().value())).thenReturn(Optional.of(transaction));

    assertThatThrownBy(() -> service.clear(transaction.id()))
        .isInstanceOf(AccountNotFoundException.class);
    assertThat(transaction.isAborted()).isFalse();
    verify(transactionRepository, never()).save(any());
  }

  @Test
  void accountNotVisibleYetFailsRollback() {
    when(transactionRepository.findBy(transaction.id().value())).thenReturn(Optional.of(transaction));

    assertThatThrownBy(() -> service.rollback(transaction.id()))
        .isInstanceOf(AccountNotFoundException.class);
  }
}
