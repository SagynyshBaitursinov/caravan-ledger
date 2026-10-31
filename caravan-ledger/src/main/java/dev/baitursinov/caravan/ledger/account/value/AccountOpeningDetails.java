package dev.baitursinov.caravan.ledger.account.value;

import org.joda.money.CurrencyUnit;

public record AccountOpeningDetails(boolean overdraftAllowed, CurrencyUnit currency) {
}
