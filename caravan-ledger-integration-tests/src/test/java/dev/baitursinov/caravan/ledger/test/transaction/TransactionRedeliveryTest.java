package dev.baitursinov.caravan.ledger.test.transaction;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.transaction.service.settlement.TransactionSettlementService;
import dev.baitursinov.caravan.ledger.transaction.service.clearing.TransactionClearingService;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRedeliveryTest extends AbstractTransactionTest {

  @Autowired
  TransactionClearingService transactionClearingService;

  @Autowired
  TransactionSettlementService transactionSettlementService;

  @Test
  void redeliveredClearingAndSettlementOfSettledTransactionChangeNothing() {
    var from = openFundedAccount(TEN);
    var to = openAccount(false);
    var transactionId = initiateTransfer(from, to, FIVE);
    awaitSettled(transactionId);
    var versions = versionsOf(transactionId, from.id(), to.id());

    transactionClearingService.clear(transactionId);
    transactionSettlementService.settle(transactionId);

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

    transactionClearingService.rollback(transactionId);

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

    transactionClearingService.rollback(transactionId);
    transactionClearingService.clear(transactionId);
    transactionSettlementService.settle(transactionId);

    assertThat(versionsOf(transactionId, from.id(), to.id())).isEqualTo(versions);
    assertThat(reload(from).balance()).isEqualTo(FIVE);
    assertThat(reload(to).balance()).isEqualTo(ZERO);
  }

  @Test
  void unknownTransactionFails() {
    var unknown = TransactionId.random();

    assertThatThrownBy(() -> transactionClearingService.clear(unknown))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> transactionClearingService.rollback(unknown))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> transactionSettlementService.settle(unknown))
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
