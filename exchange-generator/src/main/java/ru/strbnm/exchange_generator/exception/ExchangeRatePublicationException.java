package ru.strbnm.exchange_generator.exception;

public class ExchangeRatePublicationException extends RuntimeException {

  public ExchangeRatePublicationException(String message) {
    super(message);
  }

  public ExchangeRatePublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
