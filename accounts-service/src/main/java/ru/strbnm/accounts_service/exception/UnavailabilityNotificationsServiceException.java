package ru.strbnm.accounts_service.exception;

public class UnavailabilityNotificationsServiceException extends RuntimeException {

  public UnavailabilityNotificationsServiceException(String message) {
    super(message);
  }

  public UnavailabilityNotificationsServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
