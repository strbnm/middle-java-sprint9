package ru.strbnm.exchange_generator.exception;

public class UnavailabilityPaymentServiceException extends ExchangeRatePublicationException {

  public UnavailabilityPaymentServiceException(String message) {
    super(message);
  }

  public UnavailabilityPaymentServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
