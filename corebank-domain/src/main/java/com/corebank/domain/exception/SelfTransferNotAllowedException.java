package com.corebank.domain.exception;

public class SelfTransferNotAllowedException extends RuntimeException {
  public SelfTransferNotAllowedException(String message) {
    super(message);
  }
}
