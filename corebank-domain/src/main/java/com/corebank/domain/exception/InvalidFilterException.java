package com.corebank.domain.exception;

public class InvalidFilterException extends RuntimeException {
  public InvalidFilterException(String message) {
    super(message);
  }
}
