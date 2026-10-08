package com.ncba.countryintegration.exception;

/** A stored record does not exist. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}
