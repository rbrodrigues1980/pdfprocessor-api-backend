package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class Invalid2FACodeException extends RuntimeException {
    public Invalid2FACodeException(String message) {
        super(message);
    }
}

