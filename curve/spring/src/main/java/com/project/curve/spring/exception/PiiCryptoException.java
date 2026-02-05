package com.project.curve.spring.exception;

public class PiiCryptoException extends RuntimeException {

    public PiiCryptoException(String message) {
        super(message);
    }

    public PiiCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
