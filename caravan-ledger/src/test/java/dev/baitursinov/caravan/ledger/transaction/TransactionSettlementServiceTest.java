package dev.baitursinov.caravan.ledger.transaction;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.entity.AccountRepository;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.entity.TransactionRepository;
import dev.baitursinov.caravan.ledger.transaction.service.settlement.TransactionSettlementService;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static dev.baitursinov.caravan.ledger.account.entity.Account.DEFAULT_CURRENCY;
import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.CREDIT;
import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionSettlementServiceTest {

  private static final Money FIVE = Money.of(DEFAULT_CURRENCY, 5);

  private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final TransactionSettlementService service =
      new TransactionSettlementService(transactionRepository, accountRepository);

  @Test
  void redeliveryAfterPartialSettlementSettlesRemainingLegs() {
    var from = Account.open(true);
    var to = Account.open(false);
    var transaction = Transaction.start(
        ZonedDateTime.now(),
        List.of(
            TransactionLeg.of(from.id(), DEBIT, FIVE),
            TransactionLeg.of(to.id(), CREDIT, FIVE)));
    from.hold(transaction);
    to.hold(transaction);
    transaction.complete();
    // The previous delivery saved the debit leg and failed before the credit leg
    from.settle(transaction);

    when(transactionRepository.findBy(transaction.id().value())).thenReturn(Optional.of(transaction));
    when(accountRepository.findBy(from.id().value())).thenReturn(Optional.of(from));
    when(accountRepository.findBy(to.id().value())).thenReturn(Optional.of(to));

    service.settle(transaction.id());

    assertThat(to.hasSettledTransaction(transaction.id())).isTrue();
    assertThat(to.hasHeldTransaction(transaction.id())).isFalse();
    assertThat(to.balance()).isEqualTo(FIVE);
    verify(accountRepository).save(to);
    verify(accountRepository, never()).save(from);
  }
}
