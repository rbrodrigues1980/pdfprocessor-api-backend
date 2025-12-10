package br.com.verticelabs.pdfprocessor.infrastructure.util;

import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import org.springframework.stereotype.Service;

/**
 * Implementação da validação de CPF conforme as regras da Receita Federal do Brasil.
 * 
 * Algoritmo Mod11:
 * 1. Valida formato (11 dígitos)
 * 2. Rejeita CPFs com todos os dígitos iguais
 * 3. Calcula e valida os dois dígitos verificadores
 */
@Service
public class CpfValidationServiceImpl implements CpfValidationService {

    @Override
    public boolean isValid(String cpf) {
        if (cpf == null || cpf.isEmpty()) {
            return false;
        }
        
        String normalized = normalize(cpf);
        
        // CPF deve ter exatamente 11 dígitos
        if (normalized.length() != 11) {
            return false;
        }
        
        // Verifica se todos os dígitos são iguais (CPFs inválidos conhecidos)
        // Exemplos: 111.111.111-11, 222.222.222-22, etc.
        if (isAllDigitsEqual(normalized)) {
            return false;
        }
        
        // Validação dos dígitos verificadores usando algoritmo Mod11 da Receita Federal
        return validateMod11(normalized);
    }

    @Override
    public String normalize(String cpf) {
        if (cpf == null) {
            return "";
        }
        // Remove todos os caracteres não numéricos (pontos, traços, espaços, etc.)
        return cpf.replaceAll("[^0-9]", "");
    }

    /**
     * Verifica se todos os dígitos do CPF são iguais.
     * CPFs com todos os dígitos iguais são inválidos pela Receita Federal.
     */
    private boolean isAllDigitsEqual(String cpf) {
        if (cpf == null || cpf.isEmpty()) {
            return false;
        }
        char firstChar = cpf.charAt(0);
        for (int i = 1; i < cpf.length(); i++) {
            if (cpf.charAt(i) != firstChar) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valida os dígitos verificadores do CPF usando o algoritmo Mod11 da Receita Federal.
     * 
     * Algoritmo:
     * 1. Primeiro dígito: soma dos 9 primeiros dígitos multiplicados por 10, 9, 8, ..., 2
     *    Se o resto da divisão por 11 for menor que 2, o dígito é 0, senão é 11 - resto
     * 2. Segundo dígito: soma dos 10 primeiros dígitos (incluindo o primeiro verificador) 
     *    multiplicados por 11, 10, 9, ..., 2
     *    Se o resto da divisão por 11 for menor que 2, o dígito é 0, senão é 11 - resto
     */
    private boolean validateMod11(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return false;
        }
        
        try {
            // Validação do primeiro dígito verificador
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                int digit = Character.getNumericValue(cpf.charAt(i));
                sum += digit * (10 - i);
            }
            
            int remainder = sum % 11;
            int firstVerifier = (remainder < 2) ? 0 : (11 - remainder);
            
            int firstDigit = Character.getNumericValue(cpf.charAt(9));
            if (firstVerifier != firstDigit) {
                return false;
            }
            
            // Validação do segundo dígito verificador
            sum = 0;
            for (int i = 0; i < 10; i++) {
                int digit = Character.getNumericValue(cpf.charAt(i));
                sum += digit * (11 - i);
            }
            
            remainder = sum % 11;
            int secondVerifier = (remainder < 2) ? 0 : (11 - remainder);
            
            int secondDigit = Character.getNumericValue(cpf.charAt(10));
            return secondVerifier == secondDigit;
            
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            // Se houver erro ao converter caracteres, CPF é inválido
            return false;
        }
    }
}

