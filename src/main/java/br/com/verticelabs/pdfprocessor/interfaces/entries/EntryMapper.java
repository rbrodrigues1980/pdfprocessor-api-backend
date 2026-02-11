package br.com.verticelabs.pdfprocessor.interfaces.entries;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import br.com.verticelabs.pdfprocessor.interfaces.entries.dto.EntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class EntryMapper {

    private final RubricaRepository rubricaRepository;

    /**
     * Converte PayrollEntry para EntryResponse sem buscar informações da rubrica.
     * Use este método quando não precisar de informações adicionais da rubrica.
     */
    public EntryResponse toResponse(PayrollEntry entry) {
        if (entry == null) {
            return null;
        }
        
        return EntryResponse.builder()
                .id(entry.getId())
                .documentId(entry.getDocumentoId())
                .rubricaCodigo(entry.getRubricaCodigo())
                .rubricaDescricao(entry.getRubricaDescricao())
                .referencia(entry.getReferencia())
                .valor(entry.getValor())
                .origem(entry.getOrigem())
                .pagina(entry.getPagina())
                .build();
    }

    /**
     * Converte PayrollEntry para EntryResponse buscando informações da rubrica quando disponível.
     * Use este método quando precisar de informações adicionais da rubrica (categoria, ativo, etc.).
     * Para entries de IRPF que não têm rubrica válida, retorna null para os campos de rubrica.
     */
    public Mono<EntryResponse> toResponseWithRubrica(PayrollEntry entry, String tenantId) {
        if (entry == null) {
            return Mono.just(null);
        }
        
        // Se não tem código de rubrica, retornar sem buscar
        if (entry.getRubricaCodigo() == null || entry.getRubricaCodigo().trim().isEmpty()) {
            return Mono.just(toResponse(entry));
        }
        
        // Normalizar código: remover espaços
        String codigoNormalizado = entry.getRubricaCodigo().trim().replaceAll("\\s+", "");
        
        // Buscar rubrica (pode não existir para entries de IRPF)
        return rubricaRepository.findByCodigo(codigoNormalizado, tenantId != null ? tenantId : "GLOBAL")
                .map(rubrica -> {
                    return EntryResponse.builder()
                            .id(entry.getId())
                            .documentId(entry.getDocumentoId())
                            .rubricaCodigo(entry.getRubricaCodigo())
                            .rubricaDescricao(entry.getRubricaDescricao() != null ? entry.getRubricaDescricao() : rubrica.getDescricao())
                            .referencia(entry.getReferencia())
                            .valor(entry.getValor())
                            .origem(entry.getOrigem())
                            .pagina(entry.getPagina())
                            .rubricaCategoria(rubrica.getCategoria())
                            .rubricaAtivo(rubrica.getAtivo())
                            .build();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Rubrica não encontrada (comum para entries de IRPF)
                    // Retornar entry sem informações adicionais da rubrica
                    return Mono.just(EntryResponse.builder()
                            .id(entry.getId())
                            .documentId(entry.getDocumentoId())
                            .rubricaCodigo(entry.getRubricaCodigo())
                            .rubricaDescricao(entry.getRubricaDescricao())
                            .referencia(entry.getReferencia())
                            .valor(entry.getValor())
                            .origem(entry.getOrigem())
                            .pagina(entry.getPagina())
                            .rubricaCategoria(null)
                            .rubricaAtivo(null)
                            .build());
                }));
    }
}

