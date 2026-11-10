package dev.baitursinov.caravan.ledger.test.transaction;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.junit.jupiter.api.Test;

import static dev.baitursinov.caravan.ledger.common.value.Direction.CREDIT;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joda.money.CurrencyUnit.EUR;

class TransactionRollbackTest extends AbstractTransactionTest {

  @Test
  void insufficientFundsAbortTransactionAndLeaveBalancesUntouched() {
    var from = openFundedAccount(FIVE);
    var to = openAccount(false);

    var transactionId = initiateTransfer(from, to, TEN);
    var transaction = awaitRolledBack(transactionId);

    assertThat(transaction.isCompleted()).isFalse();
    assertThat(reload(from).balance()).isEqualTo(FIVE);
    assertThat(reload(from).availableBalance()).isEqualTo(FIVE);
    assertThat(reload(to).balance()).isEqualTo(ZERO);
    assertThat(reload(to).hasSettledTransaction(transactionId)).isFalse();
  }

  @Test
  void debitAccountsNeverReservedAreMarkedRolledBackToo() {
    var from = openFundedAccount(FIVE);
    var to = openAccount(false);
    long toVersionBefore = reload(to).version();

    var transactionId = initiateTransfer(from, to, TEN);
    awaitRolledBack(transactionId);

    // the debit leg fails to be reserved, yet it is marked so that a replayed reservation is ignored
    assertThat(reload(from).hasReservedTransaction(transactionId)).isFalse();
    assertThat(reload(from).hasRolledBackTransaction(transactionId)).isTrue();
    // the credit leg reserves nothing, so nothing is released on its account
    assertThat(reload(to).hasRolledBackTransaction(transactionId)).isFalse();
    assertThat(reload(to).version()).isEqualTo(toVersionBefore);
  }

  @Test
  void rejectedCreditAbortsBeforeAnyDebitIsReserved() {
    var from = openFundedAccount(TEN);
    var missingAccountId = AccountId.random();
    long fromVersionBefore = reload(from).version();

    var transactionId = startTransaction(
        TransactionLeg.of(from.id(), DEBIT, TEN),
        TransactionLeg.of(missingAccountId, CREDIT, TEN));
    var transaction = awaitRolledBack(transactionId);

    assertThat(transaction.isCompleted()).isFalse();
    Account reloadedAccount = reload(from);
    assertThat(reloadedAccount.balance()).isEqualTo(TEN);
    assertThat(reloadedAccount.availableBalance()).isEqualTo(TEN);
    assertThat(reloadedAccount.hasReservedTransaction(transactionId)).isFalse();
    // only the rolled-back marker is recorded, the debit was never reserved
    assertThat(reloadedAccount.version()).isEqualTo(fromVersionBefore + 1);
  }

  @Test
  void currencyMismatchAbortsTransactionAndReleasesReservations() {
    var from = openFundedAccount(TEN);
    var euroAccount = Account.open(false, EUR);
    accountRepository.save(euroAccount);

    var transactionId = initiateTransfer(from, euroAccount, TEN);
    var transaction = awaitRolledBack(transactionId);

    assertThat(transaction.isCompleted()).isFalse();
    assertThat(reload(from).availableBalance()).isEqualTo(TEN);
    assertThat(reload(euroAccount).balance().getCurrencyUnit()).isEqualTo(EUR);
    assertThat(reload(euroAccount).balance().isZero()).isTrue();
  }

  @Test
  void failureOfAnyDebitReleasesTheOtherDebits() {
    var funded = openFundedAccount(FIVE);
    var broke = openAccount(false);
    var to = openAccount(false);

    var transactionId = startTransaction(
        TransactionLeg.of(funded.id(), DEBIT, FIVE),
        TransactionLeg.of(broke.id(), DEBIT, FIVE),
        TransactionLeg.of(to.id(), CREDIT, TEN));
    awaitRolledBack(transactionId);

    assertThat(reload(funded).balance()).isEqualTo(FIVE);
    assertThat(reload(funded).availableBalance()).isEqualTo(FIVE);
    assertThat(reload(broke).balance()).isEqualTo(ZERO);
    assertThat(reload(to).balance()).isEqualTo(ZERO);
  }

  @Test
  void rolledBackFundsCanBeSpentByNextTransaction() {
    var from = openFundedAccount(TEN);
    var to = openAccount(false);

    awaitRolledBack(initiateTransfer(from, to, usd(20)));
    awaitSettled(initiateTransfer(from, to, TEN));

    assertThat(reload(from).balance()).isEqualTo(ZERO);
    assertThat(reload(to).balance()).isEqualTo(TEN);
  }

  @Test
  void reservationArrivingAfterRollbackIsIgnored() {
    var from = openFundedAccount(FIVE);
    var to = openAccount(false);
    var transactionId = initiateTransfer(from, to, TEN);
    var transaction = awaitRolledBack(transactionId);

    var lateFromAccount = reload(from);
    long versionBeforeLateReservation = lateFromAccount.version();
    lateFromAccount.reserve(transaction);

    assertThat(lateFromAccount.version()).isEqualTo(versionBeforeLateReservation);
    assertThat(lateFromAccount.hasReservedTransaction(transactionId)).isFalse();
  }
}
