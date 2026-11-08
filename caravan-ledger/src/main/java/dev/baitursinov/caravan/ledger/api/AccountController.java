package dev.baitursinov.caravan.ledger.api;

import dev.baitursinov.caravan.ledger.account.entity.Account;
import dev.baitursinov.caravan.ledger.account.service.AccountService;
import dev.baitursinov.caravan.ledger.account.value.AccountId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @PostMapping
  public ResponseEntity<OpenAccountResponse> openAccount(
      @Valid @RequestBody OpenAccountRequest request) {

    var account = accountService.open(
        request.overdraftAllowed(),
        CurrencyUnit.of(request.currency()));

    return ResponseEntity
        .created(URI.create("/accounts/" + account.id()))
        .body(new OpenAccountResponse(account.id().value()));
  }

  @GetMapping("/{accountId}")
  public AccountResponse getAccount(
      @PathVariable String accountId) {

    return AccountResponse.of(
        accountService.get(AccountId.of(accountId)));
  }

  public record OpenAccountRequest(@NotBlank String currency,
                                   @NotNull Boolean overdraftAllowed) {
  }

  public record OpenAccountResponse(String accountId) {
  }

  public record AccountResponse(String accountId,
                                Money balance,
                                Money availableBalance,
                                boolean overdraftAllowed) {

    static AccountResponse of(Account account) {
      return new AccountResponse(
          account.id().value(),
          account.balance(),
          account.availableBalance(),
          account.isOverdraftAllowed());
    }
  }
}
