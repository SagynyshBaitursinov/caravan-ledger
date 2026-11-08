package dev.baitursinov.caravan.ledger.test.transaction;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.api.AccountController.AccountResponse;
import dev.baitursinov.caravan.ledger.api.AccountController.OpenAccountResponse;
import dev.baitursinov.caravan.ledger.api.TransactionController.StartTransactionResponse;
import dev.baitursinov.caravan.ledger.api.TransactionController.TransactionResponse;
import dev.baitursinov.caravan.ledger.test.AbstractSpringBootTest;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionStatus;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.CREDIT;
import static dev.baitursinov.caravan.ledger.account.value.LedgerEntryType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class TransactionControllerTest extends AbstractSpringBootTest {

  @Autowired
  MockMvcTester mvc;

  @Autowired
  JsonMapper jsonMapper;

  @Test
  void transferMovesMoneyBetweenAccounts() {
    var from = openAccount("EUR", true);
    var to = openAccount("EUR", false);

    var result = startTransaction(transfer("EUR", from, to, "100.50"));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    var transactionId = read(result, StartTransactionResponse.class).transactionId();
    assertThat(result).headers().hasValue("Location", "/transactions/" + transactionId);

    awaitEventually(() -> assertThat(getTransaction(transactionId).status()).isEqualTo(TransactionStatus.COMPLETED));
    awaitEventually(() -> {
      assertThat(getAccount(from).balance()).isEqualTo(Money.parse("EUR -100.50"));
      assertThat(getAccount(to).balance()).isEqualTo(Money.parse("EUR 100.50"));
    });

    var transaction = getTransaction(transactionId);
    var amount = Money.parse("EUR 100.50");
    assertThat(transaction.startTimestamp()).isNotNull();
    assertThat(transaction.legs()).containsExactlyInAnyOrder(
        TransactionLeg.of(AccountId.of(from), DEBIT, amount),
        TransactionLeg.of(AccountId.of(to), CREDIT, amount));
  }

  @Test
  void legAmountsAreReturnedAsAmountAndCurrency() {
    var from = openAccount("EUR", true);
    var to = openAccount("EUR", false);
    var transactionId = read(
        startTransaction(transfer("EUR", from, to, "12.34")),
        StartTransactionResponse.class).transactionId();

    var result = mvc.get().uri("/transactions/{id}", transactionId).exchange();

    assertThat(result).bodyJson().isLenientlyEqualTo("""
        {"legs": [
          {"amount": {"amount": 12.34, "currency": "EUR"}},
          {"amount": {"amount": 12.34, "currency": "EUR"}}]}""");
  }

  @Test
  void transfersInParallelFromOneAccountAreAllSettled() throws Exception {
    int transferCount = 20;
    var from = openAccount("CHF", true);
    var receivers = IntStream.range(0, transferCount)
        .mapToObj(_ -> openAccount("CHF", false))
        .toList();

    List<MvcTestResult> results = new ArrayList<>();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = receivers.stream()
          .map(to -> executor.submit(() -> startTransaction(transfer("CHF", from, to, "5"))))
          .toList();
      for (var future : futures) {
        results.add(future.get());
      }
    }

    results.forEach(result -> assertThat(result).hasStatus(HttpStatus.CREATED));
    receivers.forEach(to -> awaitEventually(() ->
        assertThat(getAccount(to).balance()).isEqualTo(Money.parse("CHF 5"))));
    awaitEventually(() ->
        assertThat(getAccount(from).balance()).isEqualTo(Money.parse("CHF " + -5 * transferCount)));
  }

  @Test
  void transferWithoutFundsIsAborted() {
    var from = openAccount("USD", false);
    var to = openAccount("USD", false);

    var transactionId = read(
        startTransaction(transfer("USD", from, to, "1")),
        StartTransactionResponse.class).transactionId();

    awaitEventually(() -> assertThat(getTransaction(transactionId).status()).isEqualTo(TransactionStatus.ABORTED));
    assertThat(getAccount(to).balance()).isEqualTo(Money.parse("USD 0"));
  }

  @Test
  void transferToUnknownAccountIsAborted() {
    var from = openAccount("USD", true);

    var transactionId = read(
        startTransaction(transfer("USD", from, "unknown", "1")),
        StartTransactionResponse.class).transactionId();

    awaitEventually(() -> assertThat(getTransaction(transactionId).status()).isEqualTo(TransactionStatus.ABORTED));
  }

  @Test
  void invalidTransactionsAreRejected() {
    assertRejected(transfer("XYZ", "a", "b", "1"), "malformed");
    assertRejected(transfer("USD", "a", "b", "1.001"), "malformed");
    assertRejected("""
        {"legs": [
          {"accountId": "a", "type": "DEBIT", "amount": {"amount": 2, "currency": "USD"}},
          {"accountId": "b", "type": "CREDIT", "amount": {"amount": 1, "currency": "USD"}}]}""", "sum up to zero");
    assertRejected("""
        {"legs": [
          {"accountId": "a", "type": "DEBIT", "amount": {"amount": 1, "currency": "USD"}},
          {"accountId": "b", "type": "CREDIT", "amount": {"amount": 1, "currency": "EUR"}}]}""", "one currency");
  }

  @Test
  void everyArgumentIsRequired() {
    assertRejected("{}", "legs must not be empty");
    assertRejected("""
        {"legs": []}""", "legs must not be empty");
    assertRejected("""
        {"legs": [null]}""", "legs[0] must not be null");
    assertRejected("""
        {"legs": [{"type": "DEBIT", "amount": {"amount": 1, "currency": "USD"}}]}""",
        "legs[0].accountId must not be blank");
    assertRejected("""
        {"legs": [{"accountId": "a", "amount": {"amount": 1, "currency": "USD"}}]}""",
        "legs[0].type must not be null");
    assertRejected("""
        {"legs": [{"accountId": "a", "type": "DEBIT"}]}""",
        "legs[0].amount must not be null");
    assertRejected("""
        {"legs": [{"accountId": "a", "type": "DEBIT", "amount": {"currency": "USD"}}]}""",
        "malformed");
    assertRejected("""
        {"legs": [{"accountId": "a", "type": "DEBIT", "amount": {"amount": 1}}]}""",
        "malformed");
  }

  @Test
  void amountMustBePositive() {
    assertRejected(transfer("USD", "a", "b", "-1"), "amount must be positive");
    assertRejected(transfer("USD", "a", "b", "0"), "amount must be positive");
  }

  @Test
  void malformedBodyIsRejected() {
    assertRejected("{", "malformed");
    assertRejected("""
        {"legs": [{"accountId": "a", "type": "OTHER", "amount": {"amount": 1, "currency": "USD"}}]}""", "malformed");
    assertRejected("""
        {"legs": [{"accountId": "a", "type": "DEBIT", "amount": 1}]}""", "malformed");
  }

  @Test
  void unknownTransactionIsNotFound() {
    assertThat(mvc.get().uri("/transactions/{id}", "unknown")).hasStatus(HttpStatus.NOT_FOUND);
  }

  private void assertRejected(String body,
                              String detail) {
    var result = startTransaction(body);
    assertThat(result).as(body).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).as(body).bodyJson().extractingPath("$.detail").asString().contains(detail);
  }

  private static String transfer(String currency,
                                 String from,
                                 String to,
                                 String amount) {
    return """
        {"legs": [
          {"accountId": "%1$s", "type": "DEBIT", "amount": {"amount": %3$s, "currency": "%4$s"}},
          {"accountId": "%2$s", "type": "CREDIT", "amount": {"amount": %3$s, "currency": "%4$s"}}]}"""
        .formatted(from, to, amount, currency);
  }

  private String openAccount(String currency,
                             boolean overdraftAllowed) {
    var result = mvc.post().uri("/accounts")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"currency": "%s", "overdraftAllowed": %s}""".formatted(currency, overdraftAllowed))
        .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return read(result, OpenAccountResponse.class).accountId();
  }

  private MvcTestResult startTransaction(String body) {
    return mvc.post().uri("/transactions")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private TransactionResponse getTransaction(String transactionId) {
    var result = mvc.get().uri("/transactions/{id}", transactionId).exchange();
    assertThat(result).hasStatusOk();
    return read(result, TransactionResponse.class);
  }

  private AccountResponse getAccount(String accountId) {
    var result = mvc.get().uri("/accounts/{id}", accountId).exchange();
    assertThat(result).hasStatusOk();
    return read(result, AccountResponse.class);
  }

  private <T> T read(MvcTestResult result, Class<T> type) {
    return jsonMapper.readValue(result.getResponse().getContentAsByteArray(), type);
  }
}
