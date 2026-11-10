package dev.baitursinov.caravan.ledger.account.config;

import dev.baitursinov.caravan.event.EntityEventsRegistration;
import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.value.AccountOpeningDetails;
import dev.baitursinov.caravan.ledger.account.value.ReservationDetails;
import dev.baitursinov.caravan.ledger.account.value.ReservationRejected;
import dev.baitursinov.caravan.ledger.account.value.SettlementDetails;
import dev.baitursinov.caravan.ledger.account.value.TransactionIdHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class AccountEventsConfiguration {

  public static final String OPENED = "opened";
  public static final String BALANCE_RESERVED = "balance-reserved";
  public static final String RESERVATION_REJECTED = "reservation-rejected";
  public static final String TRANSACTION_SETTLED = "transaction-settled";
  public static final String TRANSACTION_ROLLED_BACK = "transaction-rolled-back";

  @Bean
  public EntityEventsRegistration accountEventsRegistration() {
    return new EntityEventsRegistration(
        Account.ENTITY_NAME,
        Map.of(
            OPENED, AccountOpeningDetails.class,
            BALANCE_RESERVED, ReservationDetails.class,
            RESERVATION_REJECTED, ReservationRejected.class,
            TRANSACTION_SETTLED, SettlementDetails.class,
            TRANSACTION_ROLLED_BACK, TransactionIdHolder.class));
  }
}
