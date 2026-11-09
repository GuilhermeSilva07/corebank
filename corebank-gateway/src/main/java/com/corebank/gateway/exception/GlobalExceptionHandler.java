package com.corebank.gateway.exception;

import com.corebank.domain.exception.AccountAccessDeniedException;
import com.corebank.domain.exception.DestinationAccountNotFoundException;
import com.corebank.domain.exception.InsufficientBalanceException;
import com.corebank.domain.exception.InvalidFilterException;
import com.corebank.domain.exception.InvalidPaginationException;
import com.corebank.domain.exception.InvalidRefreshTokenException;
import com.corebank.domain.exception.SelfTransferNotAllowedException;
import com.corebank.domain.exception.UserAlreadyExistsException;
import com.corebank.gateway.dto.error.ApiFieldError;
import com.corebank.gateway.dto.error.ErrorResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiFieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                springFieldError ->
                    new ApiFieldError(
                        springFieldError.getField(), springFieldError.getDefaultMessage()))
            .collect(Collectors.toList());

    ErrorResponse errorResponse =
        new ErrorResponse("Invalid request", "VALIDATION_ERROR", fieldErrors);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
    ErrorResponse errorResponse = new ErrorResponse("Unauthorized", "INVALID_CREDENTIALS", null);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
  }

  @ExceptionHandler(UserAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
    ErrorResponse errorResponse = new ErrorResponse("Conflict", "USER_ALREADY_EXISTS", null);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
    ErrorResponse errorResponse = new ErrorResponse("Unauthorized", "INVALID_REFRESH_TOKEN", null);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
  }

  @ExceptionHandler(AccountAccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccountAccessDenied(AccountAccessDeniedException ex) {
    ErrorResponse errorResponse = new ErrorResponse("Forbidden", "ACCESS_DENIED", null);
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
  }

  @ExceptionHandler(DestinationAccountNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleDestinationAccountNotFound(
      DestinationAccountNotFoundException ex) {
    ErrorResponse errorResponse =
        new ErrorResponse(ex.getMessage(), "DESTINATION_ACCOUNT_NOT_FOUND", null);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
  }

  @ExceptionHandler(SelfTransferNotAllowedException.class)
  public ResponseEntity<ErrorResponse> handleSelfTransferNotAllowed(
      SelfTransferNotAllowedException ex) {
    ErrorResponse errorResponse =
        new ErrorResponse(ex.getMessage(), "SELF_TRANSFER_NOT_ALLOWED", null);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(errorResponse);
  }

  @ExceptionHandler(InsufficientBalanceException.class)
  public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
    ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), "INSUFFICIENT_BALANCE", null);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(errorResponse);
  }

  @ExceptionHandler(InvalidPaginationException.class)
  public ResponseEntity<ErrorResponse> handleInvalidPagination(InvalidPaginationException ex) {
    ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), "INVALID_PAGINATION", null);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
  }

  @ExceptionHandler(InvalidFilterException.class)
  public ResponseEntity<ErrorResponse> handleInvalidFilter(InvalidFilterException ex) {
    ErrorResponse errorResponse = new ErrorResponse(ex.getMessage(), "INVALID_FILTER", null);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
  }

  private static final Set<String> PAGINATION_PARAMS = Set.of("page", "size");

  /** A query/path parameter that cannot be converted to its type, e.g. {@code type=FOO}. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String code =
        PAGINATION_PARAMS.contains(ex.getName()) ? "INVALID_PAGINATION" : "INVALID_FILTER";
    String message = "Invalid value for parameter '" + ex.getName() + "'";
    ErrorResponse errorResponse =
        new ErrorResponse(message, code, List.of(new ApiFieldError(ex.getName(), message)));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
  }
}
