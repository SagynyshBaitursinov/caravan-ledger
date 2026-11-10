package dev.baitursinov.caravan.ledger.api;

import dev.baitursinov.caravan.ledger.account.value.AccountId;
import dev.baitursinov.caravan.ledger.common.value.Direction;
import dev.baitursinov.caravan.ledger.transaction.entity.Transaction;
import dev.baitursinov.caravan.ledger.transaction.service.TransactionService;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionId;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionLeg;
import dev.baitursinov.caravan.ledger.transaction.value.TransactionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.joda.money.Money;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private final TransactionService transactionService;

  @PostMapping
  public ResponseEntity<StartTransactionResponse> startTransaction(
      @Valid @RequestBody StartTransactionRequest request) {

    var legs = request.legs().stream()
        .map(LegRequest::toLeg)
        .toList();

    var transaction = transactionService.start(legs);

    return ResponseEntity
        .created(URI.create("/transactions/" + transaction.id()))
        .body(new StartTransactionResponse(transaction.id().value()));
  }

  @GetMapping("/{transactionId}")
  public TransactionResponse getTransaction(@PathVariable String transactionId) {
    return TransactionResponse.of(
        transactionService.get(TransactionId.of(transactionId)));
  }

  public record StartTransactionRequest(
      @NotEmpty List<@NotNull @Valid LegRequest> legs) {
  }

  public record LegRequest(@NotBlank String accountId,
                           @NotNull Direction direction,
                           @NotNull Money amount) {

    TransactionLeg toLeg() {
      return TransactionLeg.of(AccountId.of(accountId), direction, amount);
    }
  }

  public record StartTransactionResponse(String transactionId) {
  }

  public record TransactionResponse(String transactionId,
                                    TransactionStatus status,
                                    @Nullable String abortedReason,
                                    ZonedDateTime startTimestamp,
                                    List<TransactionLeg> legs) {

    static TransactionResponse of(Transaction transaction) {
      return new TransactionResponse(
          transaction.id().value(),
          transaction.status(),
          transaction.abortedReason(),
          transaction.startTimestamp(),
          transaction.legs());
    }
  }
}
