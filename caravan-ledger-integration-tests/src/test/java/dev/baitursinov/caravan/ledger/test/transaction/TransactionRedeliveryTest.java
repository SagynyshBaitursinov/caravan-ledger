package dev.baitursinov.caravan.ledger.test.transaction;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.clearing.ClearingService;
import dev.baitursinov.caravan.ledger.settlement.SettlementService;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRedeliveryTest extends AbstractTransactionTest {

  @Autowired
  ClearingService clearingService;

  @Autowired
  SettlementService settlementService;

  @Test
  void redeliveredClearingAndSettlementOfSettledTransactionChangeNothing() {
    var from = openFundedAccount(TEN);
    var to = openAccount(false);
    var transactionId = initiateTransfer(from, to, FIVE);
    awaitSettled(transactionId);
    var versions = versionsOf(transactionId, from.id(), to.id());

    clearingService.clear(transactionId);
    settlementService.settle(transactionId);

    assertThat(versionsOf(transactionId, from.id(), to.id())).isEqualTo(versions);
    assertThat(reload(from).balance()).isEqualTo(FIVE);
    assertThat(reload(to).balance()).isEqualTo(FIVE);
  }

  @Test
  void rollbackOfCompletedTransactionIsIgnored() {
    var from = openFundedAccount(TEN);
    var to = openAccount(false);
    var transactionId = initiateTransfer(from, to, FIVE);
    awaitSettled(transactionId);
    var versions = versionsOf(transactionId, from.id(), to.id());

    clearingService.rollback(transactionId);

    assertThat(versionsOf(transactionId, from.id(), to.id())).isEqualTo(versions);
    assertThat(reload(from).balance()).isEqualTo(FIVE);
    assertThat(reload(to).balance()).isEqualTo(FIVE);
  }

  @Test
  void redeliveredRollbackAndClearingOfAbortedTransactionChangeNothing() {
    var from = openFundedAccount(FIVE);
    var to = openAccount(false);
    var transactionId = initiateTransfer(from, to, TEN);
    awaitRolledBack(transactionId);
    var versions = versionsOf(transactionId, from.id(), to.id());

    clearingService.rollback(transactionId);
    clearingService.clear(transactionId);
    settlementService.settle(transactionId);

    assertThat(versionsOf(transactionId, from.id(), to.id())).isEqualTo(versions);
    assertThat(reload(from).balance()).isEqualTo(FIVE);
    assertThat(reload(to).balance()).isEqualTo(ZERO);
  }

  @Test
  void unknownTransactionFails() {
    var unknown = TransactionId.random();

    assertThatThrownBy(() -> clearingService.clear(unknown))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> clearingService.rollback(unknown))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> settlementService.settle(unknown))
        .isInstanceOf(IllegalStateException.class);
  }

  private List<Long> versionsOf(TransactionId transactionId, AccountId... accountIds) {
    var versions = new ArrayList<Long>();
    versions.add(load(transactionId).version());
    for (AccountId accountId : accountIds) {
      versions.add(load(accountId).version());
    }
    return versions;
  }
}
