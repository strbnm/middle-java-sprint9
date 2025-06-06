package ru.strbnm.front_ui.exception;

public class CashServiceException extends RuntimeException {

  public CashServiceException(String message) {
    super(message);
  }

  public CashServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
