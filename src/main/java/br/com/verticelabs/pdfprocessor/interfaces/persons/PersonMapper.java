package br.com.verticelabs.pdfprocessor.interfaces.persons;

import br.com.verticelabs.pdfprocessor.application.persons.PersonRubricasMatrixUseCase;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.PersonResponse;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.PersonRubricasMatrixResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PersonMapper {
    
    public PersonResponse toResponse(Person person) {
        // Garantir que a lista de documentos seja mutável (evita UnsupportedOperationException)
        java.util.List<String> documentos = person.getDocumentos() != null 
                ? new java.util.ArrayList<>(person.getDocumentos()) 
                : new java.util.ArrayList<>();
        
        return PersonResponse.builder()
                .id(person.getId())
                .tenantId(person.getTenantId())
                .cpf(person.getCpf())
                .nome(person.getNome())
                .matricula(person.getMatricula())
                .documentos(documentos)
                .ativo(person.getAtivo())
                .createdAt(person.getCreatedAt())
                .updatedAt(person.getUpdatedAt())
                .build();
    }
    
    public PersonRubricasMatrixResponse toMatrixResponse(PersonRubricasMatrixUseCase.PersonRubricasMatrixResponse useCaseResponse) {
        // Converter o Map interno para o DTO
        java.util.Map<String, java.util.Map<String, PersonRubricasMatrixResponse.RubricaMatrixCell>> matrixDto = 
                new java.util.HashMap<>();
        
        for (Map.Entry<String, Map<String, PersonRubricasMatrixUseCase.RubricaMatrixCell>> entry : 
                useCaseResponse.getMatrix().entrySet()) {
            String rubricaCodigo = entry.getKey();
            Map<String, PersonRubricasMatrixUseCase.RubricaMatrixCell> referenciaMap = entry.getValue();
            
            java.util.Map<String, PersonRubricasMatrixResponse.RubricaMatrixCell> referenciaMapDto = 
                    new java.util.HashMap<>();
            
            for (Map.Entry<String, PersonRubricasMatrixUseCase.RubricaMatrixCell> refEntry : 
                    referenciaMap.entrySet()) {
                PersonRubricasMatrixUseCase.RubricaMatrixCell cell = refEntry.getValue();
                PersonRubricasMatrixResponse.RubricaMatrixCell cellDto = 
                        PersonRubricasMatrixResponse.RubricaMatrixCell.builder()
                                .referencia(cell.getReferencia())
                                .valor(cell.getValor())
                                .quantidade(cell.getQuantidade())
                                .build();
                referenciaMapDto.put(refEntry.getKey(), cellDto);
            }
            
            matrixDto.put(rubricaCodigo, referenciaMapDto);
        }
        
        return PersonRubricasMatrixResponse.builder()
                .cpf(useCaseResponse.getCpf())
                .nome(useCaseResponse.getNome())
                .matricula(useCaseResponse.getMatricula())
                .matrix(matrixDto)
                .rubricasTotais(useCaseResponse.getRubricasTotais())
                .totalGeral(useCaseResponse.getTotalGeral())
                .build();
    }
}

