package ru.strbnm.cash_service.exception;

public class UnavailabilityNotificationsServiceException extends CashOperationException {

  public UnavailabilityNotificationsServiceException(String message) {
    super(message);
  }

  public UnavailabilityNotificationsServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
