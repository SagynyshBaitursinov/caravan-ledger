package dev.baitursinov.caravan.ledger.test.transaction;

import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.junit.jupiter.api.Test;

import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.CREDIT;
import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionSettlementTest extends AbstractTransactionTest {

  @Test
  void transferMovesMoneyBetweenAccounts() {
    var from = openFundedAccount(TEN);
    var to = openAccount(false);

    var transaction = awaitSettled(initiateTransfer(from, to, FIVE));

    assertThat(transaction.isAborted()).isFalse();
    assertThat(reload(from).balance()).isEqualTo(FIVE);
    assertThat(reload(from).availableBalance()).isEqualTo(FIVE);
    assertThat(reload(to).balance()).isEqualTo(FIVE);
    assertThat(reload(to).availableBalance()).isEqualTo(FIVE);
  }

  @Test
  void wholeBalanceCanBeTransferred() {
    var from = openFundedAccount(TEN);
    var to = openAccount(false);

    awaitSettled(initiateTransfer(from, to, TEN));

    assertThat(reload(from).balance()).isEqualTo(ZERO);
    assertThat(reload(to).balance()).isEqualTo(TEN);
  }

  @Test
  void transactionWithManyLegsSettlesEveryLeg() {
    var from = openFundedAccount(TEN);
    var firstTo = openAccount(false);
    var secondTo = openAccount(false);

    awaitSettled(
        startTransaction(
            TransactionLeg.of(from.id(), DEBIT, TEN),
            TransactionLeg.of(firstTo.id(), CREDIT, usd(4)),
            TransactionLeg.of(secondTo.id(), CREDIT, usd(6))));

    assertThat(reload(from).balance()).isEqualTo(ZERO);
    assertThat(reload(firstTo).balance()).isEqualTo(usd(4));
    assertThat(reload(secondTo).balance()).isEqualTo(usd(6));
  }

  @Test
  void transactionWithManyDebitsSettlesEveryLeg() {
    var firstFrom = openFundedAccount(FIVE);
    var secondFrom = openFundedAccount(FIVE);
    var to = openAccount(false);

    awaitSettled(
        startTransaction(
            TransactionLeg.of(firstFrom.id(), DEBIT, FIVE),
            TransactionLeg.of(secondFrom.id(), DEBIT, FIVE),
            TransactionLeg.of(to.id(), CREDIT, TEN)));

    assertThat(reload(firstFrom).balance()).isEqualTo(ZERO);
    assertThat(reload(secondFrom).balance()).isEqualTo(ZERO);
    assertThat(reload(to).balance()).isEqualTo(TEN);
  }

  @Test
  void accountWithOverdraftCanGoNegative() {
    var from = openAccount(true);
    var to = openAccount(false);

    awaitSettled(initiateTransfer(from, to, FIVE));

    assertThat(reload(from).balance()).isEqualTo(FIVE.negated());
    assertThat(reload(to).balance()).isEqualTo(FIVE);
  }

  @Test
  void receivedMoneyCanBeSpentByNextTransaction() {
    var treasury = openAccount(true);
    var middle = openAccount(false);
    var last = openAccount(false);

    awaitSettled(initiateTransfer(treasury, middle, TEN));
    awaitSettled(initiateTransfer(middle, last, TEN));

    assertThat(reload(middle).balance()).isEqualTo(ZERO);
    assertThat(reload(last).balance()).isEqualTo(TEN);
  }

  @Test
  void sequentialTransfersSpendBalanceUntilItIsExhausted() {
    var from = openFundedAccount(TEN);
    var to = openAccount(false);

    awaitSettled(initiateTransfer(from, to, FIVE));
    awaitSettled(initiateTransfer(from, to, FIVE));

    assertThat(reload(from).balance()).isEqualTo(ZERO);
    assertThat(reload(to).balance()).isEqualTo(TEN);
  }
}
