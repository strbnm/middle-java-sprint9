package ru.strbnm.transfer_service.exception;

public class UnavailabilityExchangeServiceException extends CashOperationException {

  public UnavailabilityExchangeServiceException(String message) {
    super(message);
  }

  public UnavailabilityExchangeServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
