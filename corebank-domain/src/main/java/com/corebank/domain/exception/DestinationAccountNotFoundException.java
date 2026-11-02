package com.corebank.domain.exception;

public class DestinationAccountNotFoundException extends RuntimeException {
  public DestinationAccountNotFoundException(String message) {
    super(message);
  }
}
