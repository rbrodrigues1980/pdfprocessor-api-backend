package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class EmpresaEmUsoException extends RuntimeException {
    public EmpresaEmUsoException(String id, long count) {
        super("Empresa " + id + " está vinculada a " + count + " cliente(s) e não pode ser excluída");
    }
}
