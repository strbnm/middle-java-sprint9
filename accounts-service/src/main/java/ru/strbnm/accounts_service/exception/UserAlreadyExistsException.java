package ru.strbnm.accounts_service.exception;

public class UserAlreadyExistsException extends UserOperationException {

  public UserAlreadyExistsException(String message) {
    super(message);
  }

  public UserAlreadyExistsException(String message, Throwable cause) {
    super(message, cause);
  }
}
