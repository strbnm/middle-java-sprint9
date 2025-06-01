package ru.strbnm.transfer_service.exception;

public class CashOperationException extends RuntimeException {

  public CashOperationException(String message) {
    super(message);
  }

  public CashOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
