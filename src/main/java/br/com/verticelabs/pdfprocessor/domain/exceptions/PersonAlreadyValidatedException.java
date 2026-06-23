package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class PersonAlreadyValidatedException extends RuntimeException {
    public PersonAlreadyValidatedException(String personId) {
        super("Cliente já foi marcado como validado e não pode ser alterado: " + personId);
    }
}
