package ru.strbnm.front_ui.exception;

public class UnavailabilityCashServiceException extends RuntimeException {

  public UnavailabilityCashServiceException(String message) {
    super(message);
  }

  public UnavailabilityCashServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
