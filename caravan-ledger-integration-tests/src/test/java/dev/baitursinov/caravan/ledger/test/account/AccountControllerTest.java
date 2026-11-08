package dev.baitursinov.caravan.ledger.test.account;

import dev.baitursinov.caravan.ledger.api.AccountController.AccountResponse;
import dev.baitursinov.caravan.ledger.api.AccountController.OpenAccountResponse;
import dev.baitursinov.caravan.ledger.test.AbstractSpringBootTest;
import org.joda.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class AccountControllerTest extends AbstractSpringBootTest {

  @Autowired
  MockMvcTester mvc;

  @Autowired
  JsonMapper jsonMapper;

  @Test
  void openingAccountAppliesTheRequest() {
    var result = openAccount("""
        {"currency": "EUR", "overdraftAllowed": true}""");

    assertThat(result).hasStatus(HttpStatus.CREATED);
    var opened = read(result, OpenAccountResponse.class);
    assertThat(result).headers().hasValue("Location", "/accounts/" + opened.accountId());

    var account = getAccount(opened.accountId());
    var zero = Money.parse("EUR 0");
    assertThat(account.balance()).isEqualTo(zero);
    assertThat(account.availableBalance()).isEqualTo(zero);
    assertThat(account.overdraftAllowed()).isTrue();
  }

  @Test
  void everyArgumentIsRequired() {
    assertRejected("{}", "currency must not be blank, overdraftAllowed must not be null");
    assertRejected("""
        {"currency": "EUR"}""", "overdraftAllowed must not be null");
    assertRejected("""
        {"currency": " ", "overdraftAllowed": true}""", "currency must not be blank");
    assertRejected("""
        {"overdraftAllowed": true}""", "currency must not be blank");
  }

  @Test
  void unknownCurrencyIsRejected() {
    assertRejected("""
        {"currency": "XYZ", "overdraftAllowed": false}""", "XYZ");
  }

  @Test
  void malformedBodyIsRejected() {
    assertRejected("{", "malformed");
    assertRejected("""
        {"currency": "EUR", "overdraftAllowed": "maybe"}""", "malformed");
  }

  @Test
  void unknownAccountIsNotFound() {
    assertThat(mvc.get().uri("/accounts/{id}", "unknown")).hasStatus(HttpStatus.NOT_FOUND);
  }

  private void assertRejected(String body,
                              String detail) {
    var result = openAccount(body);
    assertThat(result).as(body).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).as(body).bodyJson().extractingPath("$.detail").asString().contains(detail);
  }

  private MvcTestResult openAccount(String body) {
    return mvc.post().uri("/accounts")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
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
