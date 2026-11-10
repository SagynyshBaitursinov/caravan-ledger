package dev.baitursinov.caravan.ledger.transaction;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.common.value.Direction;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionStatus;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static dev.baitursinov.caravan.ledger.account.entity.Account.DEFAULT_CURRENCY;
import static dev.baitursinov.caravan.ledger.common.value.Direction.CREDIT;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("DataFlowIssue")
class TransactionTest {

  private static final Money TEN = Money.of(DEFAULT_CURRENCY, 10);
  private static final Money FIVE = Money.of(DEFAULT_CURRENCY, 5);

  private final ZonedDateTime startTimestamp = ZonedDateTime.now();

  @Test
  void startRecordsBalancedLegs() {
    List<TransactionLeg> legs = List.of(
        leg(AccountId.random(), DEBIT, TEN),
        leg(AccountId.random(), CREDIT, FIVE),
        leg(AccountId.random(), CREDIT, FIVE));

    var transaction = Transaction.start(startTimestamp, legs);

    assertThat(transaction.status()).isEqualTo(TransactionStatus.PENDING);
    assertThat(transaction.startTimestamp()).isEqualTo(startTimestamp);
    assertThat(transaction.legs()).containsExactlyElementsOf(legs);
    legs.forEach(leg -> assertThat(transaction.legFor(leg.accountId())).contains(leg));
    assertThat(transaction.legFor(AccountId.random())).isEmpty();
    assertThat(transaction.version()).isEqualTo(1);
  }

  @Test
  void splitsLegsByDirectionKeepingOrder() {
    var credit1 = leg(AccountId.random(), CREDIT, FIVE);
    var debit1 = leg(AccountId.random(), DEBIT, FIVE);
    var credit2 = leg(AccountId.random(), CREDIT, FIVE);
    var debit2 = leg(AccountId.random(), DEBIT, FIVE);

    var transaction = Transaction.start(startTimestamp, List.of(credit1, debit1, credit2, debit2));

    assertThat(transaction.debitLegs()).containsExactly(debit1, debit2);
    assertThat(transaction.creditLegs()).containsExactly(credit1, credit2);
  }

  @Test
  void rejectsDuplicateAccount() {
    AccountId accountId = AccountId.random();
    List<TransactionLeg> legs = List.of(
        leg(accountId, DEBIT, FIVE),
        leg(accountId, CREDIT, FIVE));

    assertRejected(legs, "more than once");
  }

  @Test
  void rejectsUnbalancedEntries() {
    assertRejected(List.of(
        leg(AccountId.random(), DEBIT, TEN),
        leg(AccountId.random(), CREDIT, FIVE)), "sum up to zero");
  }

  @Test
  void rejectsSingleSidedEntries() {
    assertRejected(List.of(
        leg(AccountId.random(), DEBIT, TEN),
        leg(AccountId.random(), DEBIT, TEN)), "sum up to zero");
  }

  @Test
  void rejectsEmptyLegs() {
    assertRejected(List.of(), "must contain legs");
  }

  @Test
  void rejectsMixedCurrencies() {
    assertRejected(List.of(
        leg(AccountId.random(), DEBIT, TEN),
        leg(AccountId.random(), CREDIT, Money.of(CurrencyUnit.EUR, 10))), "share one currency");
  }

  @Test
  void generatesRandomIdOnStart() {
    List<TransactionLeg> legs = List.of(
        leg(AccountId.random(), DEBIT, TEN),
        leg(AccountId.random(), CREDIT, TEN));

    assertThat(Transaction.start(startTimestamp, legs).id())
        .isNotEqualTo(Transaction.start(startTimestamp, legs).id());
  }

  @Test
  void rejectsNullStartArguments() {
    assertThatThrownBy(() -> Transaction.start(null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Transaction.start(startTimestamp, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsBlankId() {
    assertThatThrownBy(() -> TransactionId.of(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectionAndCompletionAreIdempotentAndExclusive() {
    var rejected = started();
    rejected.abort("no funds");
    rejected.abort("again");
    assertThat(rejected.status()).isEqualTo(TransactionStatus.ABORTED);
    assertThat(rejected.isAborted()).isTrue();
    assertThat(rejected.isCompleted()).isFalse();
    assertThat(rejected.version()).isEqualTo(2);
    assertThatThrownBy(rejected::complete).isInstanceOf(IllegalStateException.class);

    var completed = started();
    completed.complete();
    completed.complete();
    assertThat(completed.status()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(completed.isCompleted()).isTrue();
    assertThat(completed.isAborted()).isFalse();
    assertThat(completed.version()).isEqualTo(2);
    assertThatThrownBy(() -> completed.abort("late")).isInstanceOf(IllegalStateException.class);
  }

  private Transaction started() {
    return Transaction.start(startTimestamp, List.of(
        leg(AccountId.random(), DEBIT, TEN),
        leg(AccountId.random(), CREDIT, TEN)));
  }

  private void assertRejected(List<TransactionLeg> legs, String message) {
    assertThatThrownBy(() -> Transaction.start(startTimestamp, legs))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(message);
  }

  private TransactionLeg leg(AccountId accountId, Direction direction, Money amount) {
    return TransactionLeg.of(accountId, direction, amount);
  }
}
