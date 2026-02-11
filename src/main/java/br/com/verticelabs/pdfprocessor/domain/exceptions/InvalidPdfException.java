package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class InvalidPdfException extends RuntimeException {
    public InvalidPdfException(String message) {
        super(message);
    }
}

