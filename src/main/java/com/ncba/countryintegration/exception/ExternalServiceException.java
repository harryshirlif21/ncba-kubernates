package com.ncba.countryintegration.exception;

/** The upstream SOAP service failed (timeout, 5xx, bad payload, circuit open). Retried and counted by the circuit breaker. */
public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String message) { super(message); }
    public ExternalServiceException(String message, Throwable cause) { super(message, cause); }
}
