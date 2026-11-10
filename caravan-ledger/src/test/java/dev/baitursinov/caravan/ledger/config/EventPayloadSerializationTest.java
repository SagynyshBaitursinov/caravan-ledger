package dev.baitursinov.caravan.ledger.config;

import dev.baitursinov.caravan.event.EntityEventsRegistration;
import dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration;
import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.account.value.AccountOpeningDetails;
import dev.baitursinov.caravan.ledger.account.value.ReservationDetails;
import dev.baitursinov.caravan.ledger.account.value.ReservationRejected;
import dev.baitursinov.caravan.ledger.account.value.SettlementDetails;
import dev.baitursinov.caravan.ledger.account.value.TransactionIdHolder;
import dev.baitursinov.caravan.ledger.config.jackson.JacksonConfiguration;
import dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionAbortionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionCompletionDetails;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionDefinition;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.BALANCE_RESERVED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.OPENED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.RESERVATION_REJECTED;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.TRANSACTION_ROLLED_BACK;
import static dev.baitursinov.caravan.ledger.account.config.AccountEventsConfiguration.TRANSACTION_SETTLED;
import static dev.baitursinov.caravan.ledger.common.value.Direction.CREDIT;
import static dev.baitursinov.caravan.ledger.common.value.Direction.DEBIT;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.ABORTED;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.COMPLETED;
import static dev.baitursinov.caravan.ledger.transaction.config.TransactionEventsConfiguration.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

class EventPayloadSerializationTest {

  private static final JsonMapper JSON_MAPPER = new JacksonConfiguration().jsonMapper();

  private static final List<EntityEventsRegistration> REGISTRATIONS = List.of(
      new AccountEventsConfiguration().accountEventsRegistration(),
      new TransactionEventsConfiguration().transactionEventsRegistration());

  private static final AccountId ACCOUNT_ID = AccountId.of("account-1");
  private static final TransactionId TRANSACTION_ID = TransactionId.of("transaction-1");
  private static final Money TEN = Money.of(CurrencyUnit.USD, 10);
  private static final ZonedDateTime TIMESTAMP =
      ZonedDateTime.of(2026, 11, 6, 10, 15, 30, 123_000_000, ZoneOffset.UTC);

  private static final String TEN_USD_JSON = """
      {"amount": 10.00, "currency": "USD"}""";

  record Case(String entityName, String eventName, String variant, Object payload, String json) {

    @Override
    public String toString() {
      return entityName + "/" + eventName + (variant.isEmpty() ? "" : " (" + variant + ")");
    }
  }

  static Stream<Case> cases() {
    return Stream.of(
        new Case(Account.ENTITY_NAME, OPENED, "",
            new AccountOpeningDetails(true, CurrencyUnit.EUR),
            """
                {"overdraftAllowed": true, "currency": "EUR"}"""),
        new Case(Account.ENTITY_NAME, BALANCE_RESERVED, "",
            new ReservationDetails(TRANSACTION_ID, TEN),
            """
                {"transactionId": "transaction-1", "amount": %s}""".formatted(TEN_USD_JSON)),
        new Case(Account.ENTITY_NAME, RESERVATION_REJECTED, "",
            new ReservationRejected(TRANSACTION_ID, "no funds"),
            """
                {"transactionId": "transaction-1", "reason": "no funds"}"""),
        new Case(Account.ENTITY_NAME, TRANSACTION_SETTLED, "",
            SettlementDetails.builder()
                .transactionId(TRANSACTION_ID)
                .timestamp(TIMESTAMP)
                .direction(CREDIT)
                .amount(TEN)
                .build(),
            """
                {
                  "transactionId": "transaction-1",
                  "timestamp": "2026-11-06T10:15:30.123Z",
                  "direction": "CREDIT",
                  "amount": %s
                }""".formatted(TEN_USD_JSON)),
        new Case(Account.ENTITY_NAME, TRANSACTION_ROLLED_BACK, "",
            new TransactionIdHolder(TRANSACTION_ID),
            """
                {"transactionId": "transaction-1"}"""),
        new Case(Transaction.ENTITY_NAME, STARTED, "",
            TransactionDefinition.of(TIMESTAMP, List.of(
                TransactionLeg.of(ACCOUNT_ID, DEBIT, TEN),
                TransactionLeg.of(AccountId.of("account-2"), CREDIT, TEN))),
            """
                {
                  "transactionStartTimestamp": "2026-11-06T10:15:30.123Z",
                  "legs": [
                    {"accountId": "account-1", "direction": "DEBIT", "amount": %1$s},
                    {"accountId": "account-2", "direction": "CREDIT", "amount": %1$s}
                  ]
                }""".formatted(TEN_USD_JSON)),
        new Case(Transaction.ENTITY_NAME, ABORTED, "",
            new TransactionAbortionDetails("no funds"),
            """
                {"reason": "no funds"}"""),
        new Case(Transaction.ENTITY_NAME, COMPLETED, "",
            new TransactionCompletionDetails(),
            "{}"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void serializesToExpectedJson(Case testCase) {
    var json = JSON_MAPPER.writeValueAsString(testCase.payload());

    assertThat(JSON_MAPPER.readTree(json)).isEqualTo(JSON_MAPPER.readTree(testCase.json()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void deserializesFromExpectedJsonAsRegisteredPayloadClass(Case testCase) {
    Class<?> payloadClass = registeredPayloadClass(testCase);

    var payload = JSON_MAPPER.readValue(testCase.json(), payloadClass);

    assertThat(payload).isEqualTo(testCase.payload());
  }

  @Test
  void everyRegisteredEventHasCase() {
    Set<String> registered = REGISTRATIONS.stream()
        .flatMap(registration -> registration.eventToPayloadClass().keySet().stream()
            .map(eventName -> registration.entityName() + "/" + eventName))
        .collect(Collectors.toSet());
    Set<String> covered = cases()
        .map(testCase -> testCase.entityName() + "/" + testCase.eventName())
        .collect(Collectors.toSet());

    assertThat(covered).isEqualTo(registered);
  }

  @Test
  void nonUtcTimestampKeepsInstantButIsReadBackInUtc() {
    var almatyTimestamp = TIMESTAMP.withZoneSameInstant(ZoneId.of("Asia/Almaty"));
    var definition = TransactionDefinition.of(almatyTimestamp, List.of(
        TransactionLeg.of(ACCOUNT_ID, DEBIT, TEN),
        TransactionLeg.of(AccountId.of("account-2"), CREDIT, TEN)));

    var restored = JSON_MAPPER.readValue(
        JSON_MAPPER.writeValueAsString(definition), TransactionDefinition.class);

    assertThat(restored.transactionStartTimestamp().toInstant()).isEqualTo(almatyTimestamp.toInstant());
    assertThat(restored.transactionStartTimestamp().getZone()).isEqualTo(ZoneOffset.UTC);
  }

  private static Class<?> registeredPayloadClass(Case testCase) {
    Map<String, Class<?>> eventToPayloadClass = REGISTRATIONS.stream()
        .filter(registration -> registration.entityName().equals(testCase.entityName()))
        .findFirst()
        .orElseThrow()
        .eventToPayloadClass();
    Class<?> payloadClass = eventToPayloadClass.get(testCase.eventName());
    assertThat(payloadClass).as("registered payload class of %s", testCase).isNotNull();
    assertThat(testCase.payload()).isInstanceOf(payloadClass);
    return payloadClass;
  }
}
