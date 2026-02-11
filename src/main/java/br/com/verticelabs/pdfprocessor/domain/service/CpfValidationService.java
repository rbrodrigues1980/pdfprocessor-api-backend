package br.com.verticelabs.pdfprocessor.domain.service;

public interface CpfValidationService {
    boolean isValid(String cpf);
    
    String normalize(String cpf);
}

