package ru.strbnm.front_ui.exception;

public class UnavailabilityAccountsServiceException extends RuntimeException {

  public UnavailabilityAccountsServiceException(String message) {
    super(message);
  }

  public UnavailabilityAccountsServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
