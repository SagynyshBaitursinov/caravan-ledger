package dev.baitursinov.caravan.ledger.test.transaction;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.entity.AccountRepository;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.test.AbstractSpringBootTest;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.entity.TransactionRepository;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.joda.money.Money;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import static dev.baitursinov.caravan.ledger.common.value.Direction.CREDIT;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractTransactionTest extends AbstractSpringBootTest {

  @Autowired
  AccountRepository accountRepository;

  @Autowired
  TransactionRepository transactionRepository;

  protected Account openAccount(boolean overdraftAllowed) {
    var account = Account.open(overdraftAllowed);
    accountRepository.save(account);
    return account;
  }

  protected Account openFundedAccount(Money amount) {
    var treasury = openAccount(true);
    var account = openAccount(false);

    awaitSettled(initiateTransfer(treasury, account, amount));
    assertThat(reload(account).balance()).isEqualTo(amount);
    return account;
  }

  protected TransactionId initiateTransfer(Account from,
                                           Account to,
                                           Money amount) {
    return startTransaction(
        TransactionLeg.of(from.id(), DEBIT, amount),
        TransactionLeg.of(to.id(), CREDIT, amount));
  }

  protected TransactionId startTransaction(TransactionLeg... legs) {
    var transaction = Transaction.start(ZonedDateTime.now(), List.of(legs));
    transactionRepository.save(transaction);
    return transaction.id();
  }

  protected Account load(AccountId accountId) {
    return accountRepository.findBy(accountId.value()).orElseThrow();
  }

  protected Account reload(Account account) {
    return load(account.id());
  }

  protected Transaction load(TransactionId transactionId) {
    return transactionRepository.findBy(transactionId.value()).orElseThrow();
  }

  protected Transaction awaitSettled(TransactionId transactionId) {
    awaitEventually(() -> assertThat(load(transactionId).isCompleted()).isTrue());

    Transaction transaction = load(transactionId);
    transaction.legs().forEach(leg ->
        awaitEventually(() ->
            assertThat(load(leg.accountId()).hasSettledTransaction(transactionId)).isTrue()));
    return transaction;
  }

  protected Transaction awaitRolledBack(TransactionId transactionId) {
    awaitEventually(() -> assertThat(load(transactionId).isAborted()).isTrue());

    Transaction transaction = load(transactionId);
    debitAccountIds(transaction).forEach(accountId ->
        awaitEventually(() ->
            assertThat(accountRepository.findBy(accountId.value()))
                .hasValueSatisfying(account -> assertThat(account.hasRolledBackTransaction(transactionId)).isTrue())));
    return transaction;
  }

  protected Transaction awaitFinished(TransactionId transactionId) {
    awaitEventually(() -> {
      var transaction = load(transactionId);
      assertThat(transaction.isCompleted() || transaction.isAborted()).isTrue();
    });

    return load(transactionId).isCompleted()
        ? awaitSettled(transactionId)
        : awaitRolledBack(transactionId);
  }

  protected Stream<AccountId> debitAccountIds(Transaction transaction) {
    return transaction.debitLegs().stream()
        .map(TransactionLeg::accountId)
        .filter(accountId -> accountRepository.findBy(accountId.value()).isPresent());
  }
}
