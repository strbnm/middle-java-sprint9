package ru.strbnm.accounts_service.exception;

public class ExchangeServiceException extends RuntimeException {

  public ExchangeServiceException(String message) {
    super(message);
  }

  public ExchangeServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
