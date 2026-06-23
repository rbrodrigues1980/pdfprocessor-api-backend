package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class RepasseNotFoundException extends RuntimeException {
    public RepasseNotFoundException(String id) {
        super("Repasse não encontrado: " + id);
    }
}
