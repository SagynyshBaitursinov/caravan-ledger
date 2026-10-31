package dev.baitursinov.caravan.ledger.transaction.entity;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TransactionValidator {

  static void validate(List<TransactionLeg> legs) {
    if (legs.isEmpty()) {
      throw new IllegalArgumentException("Transaction must contain legs");
    }

    Set<AccountId> accountIds = new HashSet<>();
    CurrencyUnit currency = legs.getFirst().amount().getCurrencyUnit();
    Money balance = Money.zero(currency);

    for (TransactionLeg leg : legs) {
      if (!accountIds.add(leg.accountId())) {
        throw new IllegalArgumentException(
            "Transaction cannot be applied to account " + leg.accountId() + " more than once");
      }
      if (!leg.amount().getCurrencyUnit().equals(currency)) {
        throw new IllegalArgumentException(
            "Legs must share one currency, got " + currency + " and " + leg.amount().getCurrencyUnit());
      }
      balance = switch (leg.type()) {
        case CREDIT -> balance.plus(leg.amount());
        case DEBIT -> balance.minus(leg.amount());
      };
    }

    if (!balance.isZero()) {
      throw new IllegalArgumentException("Credits and debits must sum up to zero, got " + balance);
    }
  }
}
