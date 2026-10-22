package com.corebank.domain.exception;

public class InvalidPaginationException extends RuntimeException {
  public InvalidPaginationException(String message) {
    super(message);
  }
}
