package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class DocumentoDuplicadoException extends RuntimeException {
    public DocumentoDuplicadoException(String message) {
        super(message);
    }
}

