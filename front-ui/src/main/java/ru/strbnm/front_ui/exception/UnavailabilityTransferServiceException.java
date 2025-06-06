package ru.strbnm.front_ui.exception;

public class UnavailabilityTransferServiceException extends RuntimeException {

  public UnavailabilityTransferServiceException(String message) {
    super(message);
  }

  public UnavailabilityTransferServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
