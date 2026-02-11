package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class PersonDuplicadaException extends RuntimeException {
    public PersonDuplicadaException(String cpf) {
        super("Pessoa já cadastrada com CPF: " + cpf);
    }
    
    public PersonDuplicadaException(String cpf, String tenantId) {
        super("Pessoa já cadastrada com CPF: " + cpf + " no tenant: " + tenantId);
    }
}

