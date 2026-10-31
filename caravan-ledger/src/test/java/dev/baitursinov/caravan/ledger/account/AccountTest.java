package dev.baitursinov.caravan.ledger.account;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.exception.InsufficientFundsException;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.account.value.LedgerEntryType;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.joda.money.CurrencyMismatchException;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static dev.baitursinov.caravan.ledger.account.entity.Account.DEFAULT_CURRENCY;
import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.CREDIT;
import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joda.money.CurrencyUnit.EUR;

class AccountTest {

  private static final Money TEN = Money.of(DEFAULT_CURRENCY, 10);
  private static final Money FIVE = Money.of(DEFAULT_CURRENCY, 5);

  private final Account account = Account.open(true);
  private final AccountId otherAccountId = AccountId.random();

  @Test
  void newAccountIsOpened() {
    assertThat(account.entityId()).isEqualTo(account.id().value());
    assertThat(account.entityName()).isEqualTo(Account.ENTITY_NAME);
    assertThat(account.version()).isEqualTo(1);
    assertThat(account.balance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
    assertThat(account.hasHeldTransaction(TransactionId.random())).isFalse();
  }

  @Test
  void holdingTransactionDoesNotChangeBalance() {
    var transaction = transaction(CREDIT, TEN);

    account.hold(transaction);

    assertThat(account.balance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
    assertThat(account.version()).isEqualTo(2);
    assertThat(account.hasHeldTransaction(transaction.id())).isTrue();
    assertThat(account.hasSettledTransaction(transaction.id())).isFalse();
  }

  @Test
  void settlingCreditsAccount() {
    var transaction = transaction(CREDIT, TEN);
    account.hold(transaction);

    account.settle(transaction);

    assertThat(account.balance()).isEqualTo(TEN);
    assertThat(account.version()).isEqualTo(3);
    assertThat(account.hasHeldTransaction(transaction.id())).isFalse();
    assertThat(account.hasSettledTransaction(transaction.id())).isTrue();
  }

  @Test
  void settlingDebitsAccount() {
    var transaction = transaction(DEBIT, TEN);
    account.hold(transaction);

    account.settle(transaction);

    assertThat(account.balance()).isEqualTo(TEN.negated());
    assertThat(account.version()).isEqualTo(3);
  }

  @Test
  void severalTransactionsAccumulate() {
    holdAndSettle(account, transaction(CREDIT, TEN));
    holdAndSettle(account, transaction(DEBIT, FIVE));

    assertThat(account.balance()).isEqualTo(FIVE);
    assertThat(account.version()).isEqualTo(5);
  }

  @Test
  void holdingAndSettlingSameTransactionTwiceIsIgnored() {
    var transaction = transaction(CREDIT, TEN);

    account.hold(transaction);
    account.hold(transaction);
    account.settle(transaction);
    account.settle(transaction);
    account.hold(transaction);

    assertThat(account.balance()).isEqualTo(TEN);
    assertThat(account.version()).isEqualTo(3);
  }

  @Test
  void settlingUnheldTransactionIsRejected() {
    var transaction = transaction(CREDIT, TEN);

    assertThatThrownBy(() -> account.settle(transaction))
        .isInstanceOf(IllegalStateException.class);
    assertThat(account.version()).isEqualTo(1);
  }

  @Test
  void rejectsTransactionWithoutEntryForAccount() {
    var transaction = Transaction.start(
        ZonedDateTime.now(),
        List.of(
            TransactionLeg.of(otherAccountId, CREDIT, TEN),
            TransactionLeg.of(AccountId.random(), DEBIT, TEN)));

    assertThatThrownBy(() -> account.hold(transaction))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(account.version()).isEqualTo(1);
  }

  @Test
  void rejectsBlankIds() {
    assertThatThrownBy(() -> TransactionId.of(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AccountId.of(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAmountInOtherCurrencyWithoutChangingState() {
    for (LedgerEntryType type : LedgerEntryType.values()) {
      var transaction = transaction(type, Money.of(EUR, 10));

      assertThatThrownBy(() -> account.hold(transaction))
          .isInstanceOf(CurrencyMismatchException.class);

      assertThat(account.hasHeldTransaction(transaction.id())).isFalse();
    }
    assertThat(account.version()).isEqualTo(1);
    assertThat(account.availableBalance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
  }

  @Test
  void opensWithGivenCurrency() {
    var eurAccount = Account.open(false, EUR);
    var eurTen = Money.of(EUR, 10);

    assertThat(eurAccount.balance()).isEqualTo(Money.zero(EUR));
    assertThat(eurAccount.getCurrencyUnit()).isEqualTo(EUR);

    var credit = transaction(eurAccount, CREDIT, eurTen);
    eurAccount.hold(credit);
    eurAccount.settle(credit);

    assertThat(eurAccount.balance()).isEqualTo(eurTen);
    assertThatThrownBy(() -> eurAccount.hold(transaction(eurAccount, CREDIT, TEN)))
        .isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void accountWithoutOverdraftRejectsDebitBeyondBalance() {
    var noOverdraft = Account.open(false);
    holdAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, FIVE));

    Transaction failingTransaction = transaction(noOverdraft, DEBIT, TEN);
    assertThatThrownBy(() -> noOverdraft.hold(failingTransaction))
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(noOverdraft.balance()).isEqualTo(FIVE);
    assertThat(noOverdraft.version()).isEqualTo(3);
    assertThat(noOverdraft.hasHeldTransaction(failingTransaction.id())).isFalse();
  }

  @Test
  void accountWithoutOverdraftAllowsDebitDownToZero() {
    var noOverdraft = Account.open(false);
    holdAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));

    holdAndSettle(noOverdraft, transaction(noOverdraft, DEBIT, TEN));

    assertThat(noOverdraft.balance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
  }

  @Test
  void heldCreditDoesNotCoverDebit() {
    var noOverdraft = Account.open(false);
    noOverdraft.hold(transaction(noOverdraft, CREDIT, TEN));

    assertThatThrownBy(() -> noOverdraft.hold(transaction(noOverdraft, DEBIT, FIVE)))
        .isInstanceOf(InsufficientFundsException.class);
  }

  @Test
  void heldDebitsReduceAvailableBalance() {
    var noOverdraft = Account.open(false);
    holdAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));
    noOverdraft.hold(transaction(noOverdraft, DEBIT, FIVE));

    assertThat(noOverdraft.balance()).isEqualTo(TEN);
    assertThat(noOverdraft.availableBalance()).isEqualTo(FIVE);
    assertThatThrownBy(() -> noOverdraft.hold(transaction(noOverdraft, DEBIT, TEN)))
        .isInstanceOf(InsufficientFundsException.class);
    noOverdraft.hold(transaction(noOverdraft, DEBIT, FIVE));
    assertThat(noOverdraft.availableBalance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
  }

  @Test
  void settlingDebitKeepsAvailableBalanceConsistent() {
    var noOverdraft = Account.open(false);
    holdAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));

    holdAndSettle(noOverdraft, transaction(noOverdraft, DEBIT, FIVE));

    assertThat(noOverdraft.balance()).isEqualTo(FIVE);
    assertThat(noOverdraft.availableBalance()).isEqualTo(FIVE);
  }

  @Test
  void rollingBackHeldTransactionReleasesHold() {
    var noOverdraft = Account.open(false);
    holdAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));
    var debit = transaction(noOverdraft, DEBIT, TEN);
    noOverdraft.hold(debit);

    noOverdraft.rollback(debit);
    noOverdraft.rollback(debit);

    assertThat(noOverdraft.balance()).isEqualTo(TEN);
    assertThat(noOverdraft.availableBalance()).isEqualTo(TEN);
    assertThat(noOverdraft.hasHeldTransaction(debit.id())).isFalse();
    assertThat(noOverdraft.hasRolledBackTransaction(debit.id())).isTrue();
    assertThat(noOverdraft.version()).isEqualTo(5);
    noOverdraft.hold(transaction(noOverdraft, DEBIT, TEN));
  }

  @Test
  void rollingBackUnheldTransactionBlocksItFromBeingHeld() {
    var transaction = transaction(CREDIT, TEN);

    account.rollback(transaction);
    account.hold(transaction);

    assertThat(account.version()).isEqualTo(2);
    assertThat(account.hasHeldTransaction(transaction.id())).isFalse();
    assertThat(account.hasRolledBackTransaction(transaction.id())).isTrue();
    assertThat(account.balance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
    assertThat(account.availableBalance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
  }

  @Test
  void rollbackRejectsTransactionWithoutEntryForAccount() {
    var transaction = Transaction.start(
        ZonedDateTime.now(),
        List.of(
            TransactionLeg.of(otherAccountId, CREDIT, TEN),
            TransactionLeg.of(AccountId.random(), DEBIT, TEN)));

    assertThatThrownBy(() -> account.rollback(transaction))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(account.hasRolledBackTransaction(transaction.id())).isFalse();
    assertThat(account.version()).isEqualTo(1);
  }

  @Test
  void rolledBackTransactionCannotBeSettled() {
    var transaction = transaction(CREDIT, TEN);
    account.hold(transaction);
    account.rollback(transaction);

    assertThatThrownBy(() -> account.settle(transaction))
        .isInstanceOf(IllegalStateException.class);
    assertThat(account.balance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
  }

  @Test
  void settledTransactionCannotBeRolledBack() {
    var transaction = transaction(CREDIT, TEN);
    holdAndSettle(account, transaction);

    assertThatThrownBy(() -> account.rollback(transaction))
        .isInstanceOf(IllegalStateException.class);
    assertThat(account.balance()).isEqualTo(TEN);
  }

  private static void holdAndSettle(Account target, Transaction transaction) {
    target.hold(transaction);
    target.settle(transaction);
  }

  private Transaction transaction(LedgerEntryType type, Money amount) {
    return transaction(account, type, amount);
  }

  private Transaction transaction(Account target, LedgerEntryType type, Money amount) {
    LedgerEntryType opposite = type == CREDIT ? DEBIT : CREDIT;
    return Transaction.start(
        ZonedDateTime.now(),
        List.of(
            TransactionLeg.of(target.id(), type, amount),
            TransactionLeg.of(otherAccountId, opposite, amount)));
  }
}
