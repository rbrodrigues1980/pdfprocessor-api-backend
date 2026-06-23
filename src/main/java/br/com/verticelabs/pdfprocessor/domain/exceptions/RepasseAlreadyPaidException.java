package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class RepasseAlreadyPaidException extends RuntimeException {
    public RepasseAlreadyPaidException(String id) {
        super("Repasse já foi pago e não pode ser alterado: " + id);
    }
}
