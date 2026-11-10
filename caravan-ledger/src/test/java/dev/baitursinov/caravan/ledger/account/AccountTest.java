package dev.baitursinov.caravan.ledger.account;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.common.value.Direction;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.joda.money.CurrencyMismatchException;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static dev.baitursinov.caravan.ledger.account.entity.Account.DEFAULT_CURRENCY;
import static dev.baitursinov.caravan.ledger.common.value.Direction.CREDIT;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;
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
    assertThat(account.hasReservedTransaction(TransactionId.random())).isFalse();
  }

  @Test
  void reservingTransactionDoesNotChangeBalance() {
    var transaction = transaction(DEBIT, TEN);

    account.reserve(transaction);

    assertThat(account.balance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
    assertThat(account.version()).isEqualTo(2);
    assertThat(account.hasReservedTransaction(transaction.id())).isTrue();
    assertThat(account.hasSettledTransaction(transaction.id())).isFalse();
  }

  @Test
  void reservingCreditIsRejected() {
    var transaction = transaction(CREDIT, TEN);

    assertThatThrownBy(() -> account.reserve(transaction))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(account.version()).isEqualTo(1);
  }

  @Test
  void validatingCurrencyRecordsNothing() {
    for (Direction direction : Direction.values()) {
      account.validateCurrency(TransactionLeg.of(account.id(), direction, TEN));
    }

    assertThat(account.version()).isEqualTo(1);
  }

  @Test
  void settlingCreditsAccount() {
    var transaction = transaction(CREDIT, TEN);

    account.settle(transaction);

    assertThat(account.balance()).isEqualTo(TEN);
    assertThat(account.version()).isEqualTo(2);
    assertThat(account.hasReservedTransaction(transaction.id())).isFalse();
    assertThat(account.hasSettledTransaction(transaction.id())).isTrue();
  }

  @Test
  void settlingDebitsAccount() {
    var transaction = transaction(DEBIT, TEN);
    account.reserve(transaction);

    account.settle(transaction);

    assertThat(account.balance()).isEqualTo(TEN.negated());
    assertThat(account.version()).isEqualTo(3);
  }

  @Test
  void severalTransactionsAccumulate() {
    reserveAndSettle(account, transaction(CREDIT, TEN));
    reserveAndSettle(account, transaction(DEBIT, FIVE));

    assertThat(account.balance()).isEqualTo(FIVE);
    assertThat(account.version()).isEqualTo(4);
  }

  @Test
  void reservingAndSettlingSameTransactionTwiceIsIgnored() {
    var transaction = transaction(CREDIT, TEN);

    account.settle(transaction);
    account.settle(transaction);

    assertThat(account.balance()).isEqualTo(TEN);
    assertThat(account.version()).isEqualTo(2);
  }

  @Test
  void settlingUnreservedDebitIsRejected() {
    var transaction = transaction(DEBIT, TEN);

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

    assertThatThrownBy(() -> account.reserve(transaction))
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
    for (Direction direction : Direction.values()) {
      var transaction = transaction(direction, Money.of(EUR, 10));
      var leg = transaction.legFor(account.id()).orElseThrow();

      assertThatThrownBy(() -> account.validateCurrency(leg))
          .isInstanceOf(CurrencyMismatchException.class);
      assertThatThrownBy(() -> reserveAndSettle(account, transaction))
          .isInstanceOf(CurrencyMismatchException.class);

      assertThat(account.hasReservedTransaction(transaction.id())).isFalse();
      assertThat(account.hasSettledTransaction(transaction.id())).isFalse();
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

    eurAccount.settle(transaction(eurAccount, CREDIT, eurTen));

    assertThat(eurAccount.balance()).isEqualTo(eurTen);
    assertThatThrownBy(() -> eurAccount.validateCurrency(TransactionLeg.of(eurAccount.id(), CREDIT, TEN)))
        .isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void accountWithoutOverdraftRejectsDebitBeyondBalance() {
    var noOverdraft = Account.open(false);
    reserveAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, FIVE));

    Transaction failingTransaction = transaction(noOverdraft, DEBIT, TEN);
    noOverdraft.reserve(failingTransaction);

    assertThat(noOverdraft.hasRejectedTransaction(failingTransaction.id())).isTrue();
    assertThat(noOverdraft.balance()).isEqualTo(FIVE);
    assertThat(noOverdraft.availableBalance()).isEqualTo(FIVE);
    assertThat(noOverdraft.version()).isEqualTo(3);
    assertThat(noOverdraft.hasReservedTransaction(failingTransaction.id())).isFalse();
  }

  @Test
  void rejectedReservationIsNotDecidedAgain() {
    var noOverdraft = Account.open(false);
    var transaction = transaction(noOverdraft, DEBIT, TEN);
    noOverdraft.reserve(transaction);

    // funds arriving later do not turn a recorded rejection into a reservation
    reserveAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));
    noOverdraft.reserve(transaction);

    assertThat(noOverdraft.hasRejectedTransaction(transaction.id())).isTrue();
    assertThat(noOverdraft.hasReservedTransaction(transaction.id())).isFalse();
    assertThat(noOverdraft.version()).isEqualTo(3);
  }

  @Test
  void replayedReservationIsNotRejectedByItsOwnReservedFunds() {
    var noOverdraft = Account.open(false);
    reserveAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));
    var reserved = transaction(noOverdraft, DEBIT, TEN);
    noOverdraft.reserve(reserved);
    long versionAfterReservation = noOverdraft.version();

    noOverdraft.reserve(reserved);

    assertThat(noOverdraft.hasReservedTransaction(reserved.id())).isTrue();
    assertThat(noOverdraft.hasRejectedTransaction(reserved.id())).isFalse();
    assertThat(noOverdraft.version()).isEqualTo(versionAfterReservation);
  }

  @Test
  void accountWithoutOverdraftAllowsDebitDownToZero() {
    var noOverdraft = Account.open(false);
    reserveAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));

    reserveAndSettle(noOverdraft, transaction(noOverdraft, DEBIT, TEN));

    assertThat(noOverdraft.balance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
  }

  @Test
  void reservedDebitsReduceAvailableBalance() {
    var noOverdraft = Account.open(false);
    reserveAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));
    noOverdraft.reserve(transaction(noOverdraft, DEBIT, FIVE));

    assertThat(noOverdraft.balance()).isEqualTo(TEN);
    assertThat(noOverdraft.availableBalance()).isEqualTo(FIVE);
    var rejected = transaction(noOverdraft, DEBIT, TEN);
    noOverdraft.reserve(rejected);
    assertThat(noOverdraft.hasRejectedTransaction(rejected.id())).isTrue();
    noOverdraft.reserve(transaction(noOverdraft, DEBIT, FIVE));
    assertThat(noOverdraft.availableBalance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
  }

  @Test
  void settlingDebitKeepsAvailableBalanceConsistent() {
    var noOverdraft = Account.open(false);
    reserveAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));

    reserveAndSettle(noOverdraft, transaction(noOverdraft, DEBIT, FIVE));

    assertThat(noOverdraft.balance()).isEqualTo(FIVE);
    assertThat(noOverdraft.availableBalance()).isEqualTo(FIVE);
  }

  @Test
  void rollingBackReservedTransactionReleasesReservation() {
    var noOverdraft = Account.open(false);
    reserveAndSettle(noOverdraft, transaction(noOverdraft, CREDIT, TEN));
    var debit = transaction(noOverdraft, DEBIT, TEN);
    noOverdraft.reserve(debit);

    noOverdraft.rollback(debit);
    noOverdraft.rollback(debit);

    assertThat(noOverdraft.balance()).isEqualTo(TEN);
    assertThat(noOverdraft.availableBalance()).isEqualTo(TEN);
    assertThat(noOverdraft.hasReservedTransaction(debit.id())).isFalse();
    assertThat(noOverdraft.hasRolledBackTransaction(debit.id())).isTrue();
    assertThat(noOverdraft.version()).isEqualTo(4);
    noOverdraft.reserve(transaction(noOverdraft, DEBIT, TEN));
  }

  @Test
  void rollingBackUnreservedTransactionBlocksItFromBeingReserved() {
    var transaction = transaction(DEBIT, TEN);

    account.rollback(transaction);
    account.reserve(transaction);

    assertThat(account.version()).isEqualTo(2);
    assertThat(account.hasReservedTransaction(transaction.id())).isFalse();
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
    var transaction = transaction(DEBIT, TEN);
    account.reserve(transaction);
    account.rollback(transaction);

    assertThatThrownBy(() -> account.settle(transaction))
        .isInstanceOf(IllegalStateException.class);
    assertThat(account.balance()).isEqualTo(Money.zero(DEFAULT_CURRENCY));
  }

  @Test
  void settledTransactionCannotBeRolledBack() {
    var transaction = transaction(DEBIT, TEN);
    reserveAndSettle(account, transaction);

    assertThatThrownBy(() -> account.rollback(transaction))
        .isInstanceOf(IllegalStateException.class);
    assertThat(account.balance()).isEqualTo(TEN.negated());
  }

  @Test
  void rollingBackCreditIsRejected() {
    var transaction = transaction(CREDIT, TEN);

    assertThatThrownBy(() -> account.rollback(transaction))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(account.hasRolledBackTransaction(transaction.id())).isFalse();
    assertThat(account.version()).isEqualTo(1);
  }

  private static void reserveAndSettle(Account target, Transaction transaction) {
    if (transaction.legFor(target.id()).orElseThrow().direction() == DEBIT) {
      target.reserve(transaction);
    }
    target.settle(transaction);
  }

  private Transaction transaction(Direction direction, Money amount) {
    return transaction(account, direction, amount);
  }

  private Transaction transaction(Account target, Direction direction, Money amount) {
    Direction opposite = direction == CREDIT ? DEBIT : CREDIT;
    return Transaction.start(
        ZonedDateTime.now(),
        List.of(
            TransactionLeg.of(target.id(), direction, amount),
            TransactionLeg.of(otherAccountId, opposite, amount)));
  }
}
