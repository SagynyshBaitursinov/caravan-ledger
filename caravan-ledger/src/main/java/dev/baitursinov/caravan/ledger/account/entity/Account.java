package dev.baitursinov.caravan.ledger.account.entity;

import dev.baitursinov.caravan.ledger.account.exception.InsufficientFundsException;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.account.value.AccountOpeningDetails;
import dev.baitursinov.caravan.ledger.account.value.LedgerEntry;
import dev.baitursinov.caravan.ledger.account.value.TransactionIdHolder;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import dev.baitursinov.caravan.event.sourcing.EntityName;
import dev.baitursinov.caravan.event.sourcing.EventSourcedEntity;
import dev.baitursinov.caravan.event.sourcing.applying.EventApplier;
import lombok.Getter;
import org.joda.money.CurrencyMismatchException;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.ENTRY_HELD;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.ENTRY_ROLLED_BACK;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.ENTRY_SETTLED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.OPENED;
import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.DEBIT;

@EntityName(Account.ENTITY_NAME)
@EventApplier(AccountEventApplier.class)
public class Account extends EventSourcedEntity {

  public static final String ENTITY_NAME = "account";
  public static final CurrencyUnit DEFAULT_CURRENCY = CurrencyUnit.USD;

  final AccountId id;
  final Map<TransactionId, LedgerEntry> heldLedgerEntries = new HashMap<>();
  final Set<TransactionId> settledTransactions = new HashSet<>();
  final Set<TransactionId> rolledBackTransactions = new HashSet<>();

  @Getter
  boolean overdraftAllowed;
  @Nullable Money balance;
  @Nullable Money heldDebits;

  Account(AccountId id) {
    this.id = Objects.requireNonNull(id, "id");
  }

  public static Account open(boolean overdraftAllowed) {
    return open(overdraftAllowed, DEFAULT_CURRENCY);
  }

  public static Account open(boolean overdraftAllowed,
                             CurrencyUnit currency) {
    Objects.requireNonNull(currency, "currency");
    var account = new Account(AccountId.random());
    account.recordEvent(OPENED, new AccountOpeningDetails(overdraftAllowed, currency));
    return account;
  }

  public void hold(Transaction transaction) {
    TransactionLeg leg = legFor(transaction);

    if (hasHeldTransaction(transaction.id())
        || hasSettledTransaction(transaction.id())
        || hasRolledBackTransaction(transaction.id())) {
      return;
    }

    if (!leg.amount().getCurrencyUnit().equals(this.getCurrencyUnit())) {
      throw new CurrencyMismatchException(this.getCurrencyUnit(), leg.amount().getCurrencyUnit());
    }

    if (!overdraftAllowed
        && leg.type() == DEBIT
        && availableBalance().isLessThan(leg.amount())) {
      throw new InsufficientFundsException(id, availableBalance(), leg.amount());
    }

    recordEvent(
        ENTRY_HELD,
        LedgerEntry.builder()
            .accountId(id)
            .transactionId(transaction.id())
            .timestamp(transaction.startTimestamp())
            .type(leg.type())
            .amount(leg.amount())
            .build());
  }

  public CurrencyUnit getCurrencyUnit() {
    return balance().getCurrencyUnit();
  }

  public void settle(Transaction transaction) {
    TransactionId transactionId = transaction.id();
    if (hasSettledTransaction(transactionId)) {
      return;
    }
    if (hasRolledBackTransaction(transactionId)) {
      throw new IllegalStateException(
          "%s is rolled back on %s".formatted(transaction, this));
    }
    if (!heldLedgerEntries.containsKey(transactionId)) {
      throw new IllegalStateException(
          "%s is not held on %s".formatted(transaction, this));
    }

    recordEvent(ENTRY_SETTLED, new TransactionIdHolder(transactionId));
  }

  public void rollback(Transaction transaction) {
    legFor(transaction);

    TransactionId transactionId = transaction.id();
    if (hasRolledBackTransaction(transactionId)) {
      return;
    }
    if (hasSettledTransaction(transactionId)) {
      throw new IllegalStateException(
          "%s is already settled on %s".formatted(transaction, this));
    }

    recordEvent(ENTRY_ROLLED_BACK, new TransactionIdHolder(transactionId));
  }

  public Money balance() {
    if (balance == null) {
      throw new IllegalStateException("%s is not opened".formatted(this));
    }
    return balance;
  }

  Money heldDebits() {
    if (heldDebits == null) {
      throw new IllegalStateException("%s is not opened".formatted(this));
    }
    return heldDebits;
  }

  public Money availableBalance() {
    return balance().minus(heldDebits());
  }

  public boolean hasRolledBackTransaction(TransactionId transactionId) {
    return this.rolledBackTransactions.contains(transactionId);
  }

  public boolean hasSettledTransaction(TransactionId transactionId) {
    return this.settledTransactions.contains(transactionId);
  }

  public boolean hasHeldTransaction(TransactionId transactionId) {
    return this.heldLedgerEntries.containsKey(transactionId);
  }

  private TransactionLeg legFor(Transaction transaction) {
    return transaction.legFor(id)
        .orElseThrow(() -> new IllegalArgumentException(
            "%s has no leg for %s"
                .formatted(transaction, this)));
  }

  public AccountId id() {
    return id;
  }

  @Override
  public String entityId() {
    return id.value();
  }
}
