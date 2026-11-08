package dev.baitursinov.caravan.ledger.api;

import dev.baitursinov.caravan.event.producer.DuplicateEventProductionException;
import dev.baitursinov.caravan.event.sourcing.EventSourcedRepositoryException;
import dev.baitursinov.caravan.ledger.account.exception.AccountNotFoundException;
import dev.baitursinov.caravan.ledger.transaction.exception.TransactionNotFoundException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
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

  @ExceptionHandler(EventSourcedRepositoryException.class)
  public ProblemDetail handleConcurrentModification(EventSourcedRepositoryException exception) {
    if (!(exception.getCause() instanceof DuplicateEventProductionException)) {
      throw exception;
    }
    return ProblemDetail.forStatusAndDetail(CONFLICT, exception.getMessage());
  }
}
