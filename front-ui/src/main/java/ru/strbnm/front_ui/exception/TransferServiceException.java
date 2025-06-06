package ru.strbnm.front_ui.exception;

public class TransferServiceException extends RuntimeException {

  public TransferServiceException(String message) {
    super(message);
  }

  public TransferServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
