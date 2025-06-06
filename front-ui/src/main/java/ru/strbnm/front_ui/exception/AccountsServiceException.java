package ru.strbnm.front_ui.exception;

public class AccountsServiceException extends RuntimeException {

  public AccountsServiceException(String message) {
    super(message);
  }

  public AccountsServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
