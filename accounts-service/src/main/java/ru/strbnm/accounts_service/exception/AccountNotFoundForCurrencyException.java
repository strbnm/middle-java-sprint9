package ru.strbnm.accounts_service.exception;

public class AccountNotFoundForCurrencyException extends UserOperationException {

  public AccountNotFoundForCurrencyException(String message) {
    super(message);
  }

  public AccountNotFoundForCurrencyException(String message, Throwable cause) {
    super(message, cause);
  }
}
