package com.ncba.countryintegration.exception;

/** The SOAP service has no country for the supplied name / ISO code (business error, not retried). */
public class CountryNotFoundException extends RuntimeException {
    public CountryNotFoundException(String message) { super(message); }
}
