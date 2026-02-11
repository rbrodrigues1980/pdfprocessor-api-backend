package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class InvalidCpfException extends RuntimeException {
    public InvalidCpfException(String message) {
        super(message);
    }
}

