package ru.strbnm.cash_service.exception;

public class UnavailabilityAccountsServiceException extends CashOperationException {

  public UnavailabilityAccountsServiceException(String message) {
    super(message);
  }

  public UnavailabilityAccountsServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
