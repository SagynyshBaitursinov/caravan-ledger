package dev.baitursinov.caravan.ledger.account.config;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.value.AccountOpeningDetails;
import dev.baitursinov.caravan.ledger.account.value.LedgerEntry;
import dev.baitursinov.caravan.ledger.account.value.TransactionIdHolder;
import dev.baitursinov.caravan.event.EntityEventsRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class AccountEventsConfiguration {

  public static final String OPENED = "opened";
  public static final String ENTRY_HELD = "entry-held";
  public static final String ENTRY_SETTLED = "entry-settled";
  public static final String ENTRY_ROLLED_BACK = "entry-rolled-back";

  @Bean
  public EntityEventsRegistration accountEventsRegistration() {
    return new EntityEventsRegistration(
        Account.ENTITY_NAME,
        Map.of(
            OPENED, AccountOpeningDetails.class,
            ENTRY_HELD, LedgerEntry.class,
            ENTRY_SETTLED, TransactionIdHolder.class,
            ENTRY_ROLLED_BACK, TransactionIdHolder.class));
  }
}
