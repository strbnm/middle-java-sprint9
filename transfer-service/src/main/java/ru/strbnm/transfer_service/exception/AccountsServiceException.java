package ru.strbnm.transfer_service.exception;

public class AccountsServiceException extends RuntimeException {

  public AccountsServiceException(String message) {
    super(message);
  }

  public AccountsServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
