package dev.baitursinov.caravan.ledger.test.transaction;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionConcurrencyTest extends AbstractTransactionTest {

  @Test
  void transactionsCompetingForTheSameFundsCannotBothSucceed() {
    var from = openFundedAccount(TEN);
    var firstTo = openAccount(false);
    var secondTo = openAccount(false);

    var first = initiateTransfer(from, firstTo, TEN);
    var second = initiateTransfer(from, secondTo, TEN);
    var firstTransaction = awaitFinished(first);
    var secondTransaction = awaitFinished(second);

    assertThat(firstTransaction.isCompleted() ^ secondTransaction.isCompleted()).isTrue();
    assertThat(reload(from).balance()).isEqualTo(ZERO);
    assertThat(reload(from).availableBalance()).isEqualTo(ZERO);
    assertThat(reload(firstTo).balance().plus(reload(secondTo).balance())).isEqualTo(TEN);
  }

  @Test
  void transactionsOnDistinctAccountsAreAllSettled() {
    var transfers = IntStream.range(0, 5)
        .mapToObj(_ -> new Transfer(openAccount(true), openAccount(false)))
        .toList();

    var transactionIds = transfers.stream()
        .map(transfer -> initiateTransfer(transfer.from(), transfer.to(), FIVE))
        .toList();
    transactionIds.forEach(this::awaitSettled);

    transfers.forEach(transfer -> {
      assertThat(reload(transfer.from()).balance()).isEqualTo(FIVE.negated());
      assertThat(reload(transfer.to()).balance()).isEqualTo(FIVE);
    });
  }

  private record Transfer(Account from, Account to) {
  }
}
