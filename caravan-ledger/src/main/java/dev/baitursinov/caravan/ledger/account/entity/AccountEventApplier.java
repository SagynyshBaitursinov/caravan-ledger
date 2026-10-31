package dev.baitursinov.caravan.ledger.account.entity;

import dev.baitursinov.caravan.ledger.account.value.AccountOpeningDetails;
import dev.baitursinov.caravan.ledger.account.value.LedgerEntry;
import dev.baitursinov.caravan.ledger.account.value.LedgerEntryType;
import dev.baitursinov.caravan.ledger.account.value.TransactionIdHolder;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.event.Event;
import dev.baitursinov.caravan.event.sourcing.applying.ApplyEvent;
import org.joda.money.Money;

import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.ENTRY_HELD;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.ENTRY_ROLLED_BACK;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.ENTRY_SETTLED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.OPENED;
import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.DEBIT;

final class AccountEventApplier {

  @ApplyEvent(OPENED)
  static void applyAccountOpened(Account account,
                                 Event<AccountOpeningDetails> event) {
    var details = event.payload();
    account.overdraftAllowed = details.overdraftAllowed();
    account.balance = Money.zero(details.currency());
    account.heldDebits = Money.zero(details.currency());
  }

  @ApplyEvent(ENTRY_HELD)
  static void applyEntryHeld(Account account,
                             Event<LedgerEntry> event) {
    LedgerEntry entry = event.payload();
    applyToHeldDebits(account, entry.type(), entry.amount());

    account.heldLedgerEntries.put(entry.transactionId(), entry);
  }

  @ApplyEvent(ENTRY_SETTLED)
  static void applyEntrySettled(Account account,
                                Event<TransactionIdHolder> event) {
    LedgerEntry entry = account.heldLedgerEntries.remove(event.payload().transactionId());
    applyToHeldDebits(account, entry.type(), entry.amount().negated());
    applyToBalance(account, entry.type(), entry.amount());

    account.settledTransactions.add(entry.transactionId());
  }

  @ApplyEvent(ENTRY_ROLLED_BACK)
  static void applyEntryRolledBack(Account account,
                                   Event<TransactionIdHolder> event) {
    TransactionId transactionId = event.payload().transactionId();
    LedgerEntry entry = account.heldLedgerEntries.remove(transactionId);
    // Null if the rollback arrived before the hold: the rolled-back marker below is still
    // recorded, so that a hold arriving again is ignored instead of locking funds of an aborted transaction
    if (entry != null) {
      applyToHeldDebits(account, entry.type(), entry.amount().negated());
    }

    account.rolledBackTransactions.add(transactionId);
  }

  private static void applyToHeldDebits(Account account,
                                        LedgerEntryType type,
                                        Money amount) {
    if (type == DEBIT) {
      account.heldDebits = account.heldDebits().plus(amount);
    }
  }

  private static void applyToBalance(Account account,
                                     LedgerEntryType type,
                                     Money amount) {
    account.balance = switch (type) {
      case CREDIT -> account.balance().plus(amount);
      case DEBIT -> account.balance().minus(amount);
    };
  }
}
