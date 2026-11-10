package dev.baitursinov.caravan.ledger.account.entity;

import dev.baitursinov.caravan.event.sourcing.EntityName;
import dev.baitursinov.caravan.event.sourcing.EventSourcedEntity;
import dev.baitursinov.caravan.event.sourcing.applying.EventApplier;
import dev.baitursinov.caravan.ledger.account.exception.InsufficientFundsException;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.account.value.AccountOpeningDetails;
import dev.baitursinov.caravan.ledger.account.value.ReservationDetails;
import dev.baitursinov.caravan.ledger.account.value.SettlementDetails;
import dev.baitursinov.caravan.ledger.account.value.TransactionIdHolder;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
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

import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.BALANCE_RESERVED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.OPENED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.TRANSACTION_ROLLED_BACK;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.TRANSACTION_SETTLED;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;

@EntityName(Account.ENTITY_NAME)
@EventApplier(AccountEventApplier.class)
public class Account extends EventSourcedEntity {

  public static final String ENTITY_NAME = "account";
  public static final CurrencyUnit DEFAULT_CURRENCY = CurrencyUnit.USD;

  final AccountId id;
  final Map<TransactionId, Money> balanceReservations = new HashMap<>();
  final Set<TransactionId> settledTransactions = new HashSet<>();
  final Set<TransactionId> rolledBackTransactions = new HashSet<>();

  @Getter
  boolean overdraftAllowed;
  @Nullable Money balance;

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

  public void reserve(Transaction transaction) {
    TransactionLeg leg = legFor(transaction);
    if (leg.direction() != DEBIT) {
      throw new IllegalArgumentException(
          "Only a DEBIT can be reserved, %s has a %s leg on %s"
              .formatted(transaction, leg.direction(), this));
    }

    if (hasReservedTransaction(transaction.id())
        || hasSettledTransaction(transaction.id())
        || hasRolledBackTransaction(transaction.id())) {
      return;
    }

    validateCurrency(leg);

    if (!overdraftAllowed && availableBalance().isLessThan(leg.amount())) {
      throw new InsufficientFundsException(id, availableBalance(), leg.amount());
    }

    recordEvent(
        BALANCE_RESERVED,
        new ReservationDetails(transaction.id(), leg.amount()));
  }

  public void validateCurrency(TransactionLeg leg) {
    if (!leg.amount().getCurrencyUnit().equals(this.getCurrencyUnit())) {
      throw new CurrencyMismatchException(this.getCurrencyUnit(), leg.amount().getCurrencyUnit());
    }
  }

  public CurrencyUnit getCurrencyUnit() {
    return balance().getCurrencyUnit();
  }

  public void settle(Transaction transaction) {
    TransactionLeg leg = legFor(transaction);

    var transactionId = transaction.id();
    if (hasSettledTransaction(transactionId)) {
      return;
    }
    if (hasRolledBackTransaction(transactionId)) {
      throw new IllegalStateException(
          "%s is rolled back on %s".formatted(transaction, this));
    }
    if (leg.direction() == DEBIT && !balanceReservations.containsKey(transactionId)) {
      throw new IllegalStateException(
          "%s is not reserved on %s".formatted(transaction, this));
    }
    validateCurrency(leg);

    recordEvent(
        TRANSACTION_SETTLED,
        SettlementDetails.builder()
            .transactionId(transactionId)
            .timestamp(transaction.startTimestamp())
            .direction(leg.direction())
            .amount(leg.amount())
            .build());
  }

  public void rollback(Transaction transaction) {
    TransactionLeg leg = legFor(transaction);
    if (leg.direction() != DEBIT) {
      throw new IllegalArgumentException(
          "Only a debit can be rolled back, %s has a %s leg on %s"
              .formatted(transaction, leg.direction(), this));
    }

    var transactionId = transaction.id();
    if (hasRolledBackTransaction(transactionId)) {
      return;
    }
    if (hasSettledTransaction(transactionId)) {
      throw new IllegalStateException(
          "%s is already settled on %s".formatted(transaction, this));
    }

    recordEvent(
        TRANSACTION_ROLLED_BACK,
        new TransactionIdHolder(transactionId));
  }

  public Money balance() {
    if (balance == null) {
      throw new IllegalStateException("%s is not opened".formatted(this));
    }
    return balance;
  }

  public Money reservedBalance() {
    return balanceReservations.values().stream()
        .reduce(
            Money.zero(getCurrencyUnit()),
            Money::plus);
  }

  public Money availableBalance() {
    return balance().minus(reservedBalance());
  }

  public boolean hasRolledBackTransaction(TransactionId transactionId) {
    return this.rolledBackTransactions.contains(transactionId);
  }

  public boolean hasSettledTransaction(TransactionId transactionId) {
    return this.settledTransactions.contains(transactionId);
  }

  public boolean hasReservedTransaction(TransactionId transactionId) {
    return this.balanceReservations.containsKey(transactionId);
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
