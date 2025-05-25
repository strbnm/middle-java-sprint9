package ru.strbnm.accounts_service.exception;

public class UserOperationException extends RuntimeException {

  public UserOperationException(String message) {
    super(message);
  }

  public UserOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
