package ru.strbnm.transfer_service.exception;

public class ExchangeServiceException extends RuntimeException {

  public ExchangeServiceException(String message) {
    super(message);
  }

  public ExchangeServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
