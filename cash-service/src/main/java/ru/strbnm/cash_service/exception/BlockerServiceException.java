package ru.strbnm.cash_service.exception;

public class BlockerServiceException extends RuntimeException {

  public BlockerServiceException(String message) {
    super(message);
  }

  public BlockerServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
