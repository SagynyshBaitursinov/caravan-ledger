package dev.baitursinov.caravan.ledger.account.entity;

import dev.baitursinov.caravan.event.Event;
import dev.baitursinov.caravan.event.sourcing.applying.ApplyEvent;
import dev.baitursinov.caravan.ledger.account.value.AccountOpeningDetails;
import dev.baitursinov.caravan.ledger.account.value.ReservationDetails;
import dev.baitursinov.caravan.ledger.account.value.SettlementDetails;
import dev.baitursinov.caravan.ledger.account.value.TransactionIdHolder;
import org.joda.money.Money;

import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.BALANCE_RESERVED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.OPENED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.TRANSACTION_ROLLED_BACK;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.TRANSACTION_SETTLED;

final class AccountEventApplier {

  @ApplyEvent(OPENED)
  static void applyAccountOpened(Account account,
                                 Event<AccountOpeningDetails> event) {
    var details = event.payload();

    account.overdraftAllowed = details.overdraftAllowed();
    account.balance = Money.zero(details.currency());
  }

  @ApplyEvent(BALANCE_RESERVED)
  static void applyBalanceReserved(Account account,
                                   Event<ReservationDetails> event) {
    ReservationDetails reservationDetails = event.payload();

    account.balanceReservations.put(
        reservationDetails.transactionId(),
        reservationDetails.amount());
  }

  @ApplyEvent(TRANSACTION_SETTLED)
  static void applyTransactionSettled(Account account,
                                      Event<SettlementDetails> event) {
    SettlementDetails settlementDetails = event.payload();

    account.settledTransactions.add(settlementDetails.transactionId());
    account.balanceReservations.remove(settlementDetails.transactionId());
    account.balance = switch (settlementDetails.direction()) {
      case CREDIT -> account.balance().plus(settlementDetails.amount());
      case DEBIT -> account.balance().minus(settlementDetails.amount());
    };
  }

  @ApplyEvent(TRANSACTION_ROLLED_BACK)
  static void applyTransactionRolledBack(Account account,
                                         Event<TransactionIdHolder> event) {
    var transactionId = event.payload().transactionId();

    account.rolledBackTransactions.add(transactionId);
    account.balanceReservations.remove(transactionId);
  }
}
