package ru.strbnm.transfer_service.exception;

public class UnavailabilityBlockerServiceException extends CashOperationException {

  public UnavailabilityBlockerServiceException(String message) {
    super(message);
  }

  public UnavailabilityBlockerServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
