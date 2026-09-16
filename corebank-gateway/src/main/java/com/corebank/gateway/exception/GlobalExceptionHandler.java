package com.corebank.gateway.exception;

import com.corebank.gateway.dto.error.ApiFieldError;
import com.corebank.gateway.dto.error.ErrorResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

  @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(
      org.springframework.security.authentication.BadCredentialsException ex) {
    ErrorResponse errorResponse = new ErrorResponse("Unauthorized", "INVALID_CREDENTIALS", null);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
  }
}
