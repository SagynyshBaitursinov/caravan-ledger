package dev.baitursinov.caravan.ledger.api;

import dev.baitursinov.caravan.ledger.account.exception.AccountNotFoundException;
import dev.baitursinov.caravan.ledger.common.InvalidInputException;
import dev.baitursinov.caravan.ledger.transaction.exception.TransactionNotFoundException;
import org.joda.money.CurrencyMismatchException;
import org.joda.money.IllegalCurrencyException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler({
      InvalidInputException.class,
      IllegalCurrencyException.class,
      CurrencyMismatchException.class
  })
  public ProblemDetail handleInvalidRequest(RuntimeException exception) {
    return ProblemDetail.forStatusAndDetail(BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleConstraintViolations(MethodArgumentNotValidException exception) {
    var detail = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + " " + error.getDefaultMessage())
        .sorted()
        .collect(Collectors.joining(", "));
    return ProblemDetail.forStatusAndDetail(BAD_REQUEST, detail);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
    return ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Request body is malformed");
  }

  @ExceptionHandler({AccountNotFoundException.class, TransactionNotFoundException.class})
  public ProblemDetail handleNotFound(RuntimeException exception) {
    return ProblemDetail.forStatusAndDetail(NOT_FOUND, exception.getMessage());
  }
}
