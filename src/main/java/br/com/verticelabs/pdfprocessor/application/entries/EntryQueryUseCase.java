package br.com.verticelabs.pdfprocessor.application.entries;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryQueryUseCase {

    private final PayrollEntryRepository entryRepository;
    private final PayrollDocumentRepository documentRepository;
    private final PersonRepository personRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Busca todas as entries de um documento.
     */
    public Flux<PayrollEntry> findByDocumentId(String documentId) {
        log.debug("Buscando entries do documento: {}", documentId);
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Documento não encontrado: " + documentId)))
                .flatMapMany(document -> entryRepository.findByDocumentoId(documentId));
    }

    /**
     * Busca entries de um documento com paginação.
     */
    public Mono<org.springframework.data.domain.Page<PayrollEntry>> findByDocumentIdPaged(
            String documentId, int page, int size, String sortBy, String sortDirection) {
        log.debug("Buscando entries paginadas do documento: {} (page: {}, size: {})", documentId, page, size);
        
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Documento não encontrado: " + documentId)))
                .flatMap(document -> {
                    Sort sort = Sort.by(Sort.Direction.fromString(sortDirection != null ? sortDirection : "asc"), 
                            sortBy != null ? sortBy : "referencia");
                    Pageable pageable = PageRequest.of(page, size, sort);
                    
                    Query query = new Query(Criteria.where("documentoId").is(documentId))
                            .with(pageable);
                    
                    // Contar total de entries (sem paginação)
                    Query countQuery = new Query(Criteria.where("documentoId").is(documentId));
                    Mono<Long> count = mongoTemplate.count(countQuery, PayrollEntry.class);
                    
                    // Buscar entries com paginação
                    Flux<PayrollEntry> entries = mongoTemplate.find(query, PayrollEntry.class);
                    
                    return Mono.zip(entries.collectList(), count)
                            .map(tuple -> {
                                List<PayrollEntry> content = tuple.getT1();
                                long total = tuple.getT2();
                                return new PageImpl<>(
                                        content, 
                                        pageable, 
                                        total
                                );
                            });
                });
    }

    /**
     * Busca todas as entries de uma pessoa (via CPF).
     */
    public Flux<PayrollEntry> findByCpf(String cpf) {
        return findByCpf(cpf, null);
    }

    /**
     * Busca todas as entries de uma pessoa (via CPF).
     * Versão que aceita tenantId explicitamente para evitar problemas com múltiplas pessoas com mesmo CPF.
     * 
     * @param cpf CPF da pessoa
     * @param tenantId ID do tenant da pessoa (opcional, se null usa multi-tenancy do contexto)
     */
    public Flux<PayrollEntry> findByCpf(String cpf, String tenantId) {
        log.debug("Buscando entries do CPF: {} (tenantId: {})", cpf, tenantId);
        
        if (tenantId != null && !tenantId.isEmpty()) {
            // Se tenantId foi fornecido explicitamente, usar ele diretamente
            return findEntriesByCpfForTenant(tenantId, cpf);
        }
        
        // Se não, usar multi-tenancy do contexto de segurança
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMapMany(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        // SUPER_ADMIN: busca entries de qualquer tenant
                        return findEntriesByCpfForSuperAdmin(cpf);
                    } else {
                        // Outros usuários: busca apenas entries do seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMapMany(ctxTenantId -> findEntriesByCpfForTenant(ctxTenantId, cpf));
                    }
                });
    }

    private Flux<PayrollEntry> findEntriesByCpfForSuperAdmin(String cpf) {
        // SUPER_ADMIN: busca pessoa sem filtrar por tenantId
        return personRepository.findByCpf(cpf)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Pessoa não encontrada com CPF: " + cpf)))
                .flatMapMany(person -> {
                    if (person.getDocumentos() == null || person.getDocumentos().isEmpty()) {
                        log.debug("Pessoa não possui documentos");
                        return Flux.empty();
                    }
                    
                    // Busca entries de todos os documentos da pessoa (sem filtrar por tenantId)
                    return Flux.fromIterable(person.getDocumentos())
                            .flatMap(documentId -> entryRepository.findByDocumentoId(documentId));
                });
    }

    private Flux<PayrollEntry> findEntriesByCpfForTenant(String tenantId, String cpf) {
        // Busca pessoa filtrando por tenantId
        return personRepository.findByTenantIdAndCpf(tenantId, cpf)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Pessoa não encontrada com CPF: " + cpf + " no tenant: " + tenantId)))
                .flatMapMany(person -> {
                    if (person.getDocumentos() == null || person.getDocumentos().isEmpty()) {
                        log.debug("Pessoa não possui documentos");
                        return Flux.empty();
                    }
                    
                    // Busca entries de todos os documentos da pessoa
                    // Os documentos já são do tenant correto (garantido pelo findByTenantIdAndCpf)
                    return Flux.fromIterable(person.getDocumentos())
                            .flatMap(documentId -> entryRepository.findByDocumentoId(documentId));
                });
    }

    /**
     * Busca entries com filtros dinâmicos.
     */
    public Flux<PayrollEntry> findWithFilters(
            String cpf,
            String rubricaCodigo,
            Integer ano,
            Integer mes,
            String origem,
            String documentoId,
            Double minValor,
            Double maxValor) {
        
        log.debug("Buscando entries com filtros - cpf: {}, rubrica: {}, ano: {}, mes: {}, origem: {}, documentoId: {}, minValor: {}, maxValor: {}", 
                cpf, rubricaCodigo, ano, mes, origem, documentoId, minValor, maxValor);
        
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();
        
        // Filtro por documento
        if (documentoId != null && !documentoId.isEmpty()) {
            criteriaList.add(Criteria.where("documentoId").is(documentoId));
        }
        
        // Filtro por CPF (precisa buscar documentos da pessoa primeiro)
        if (cpf != null && !cpf.isEmpty()) {
            return ReactiveSecurityContextHelper.isSuperAdmin()
                    .flatMapMany(isSuperAdmin -> {
                        if (isSuperAdmin) {
                            // SUPER_ADMIN: busca pessoa sem filtrar por tenantId
                            return personRepository.findByCpf(cpf)
                                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Pessoa não encontrada com CPF: " + cpf)))
                                    .flatMapMany(person -> processPersonDocuments(person, criteriaList, query, rubricaCodigo, ano, mes, origem, minValor, maxValor));
                        } else {
                            // Outros usuários: busca pessoa filtrando por tenantId
                            return ReactiveSecurityContextHelper.getTenantId()
                                    .flatMapMany(tenantId -> 
                                        personRepository.findByTenantIdAndCpf(tenantId, cpf)
                                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Pessoa não encontrada com CPF: " + cpf)))
                                                .flatMapMany(person -> processPersonDocuments(person, criteriaList, query, rubricaCodigo, ano, mes, origem, minValor, maxValor))
                                    );
                        }
                    });
        }
        
        // Aplica outros filtros
        applyFilters(criteriaList, rubricaCodigo, ano, mes, origem, minValor, maxValor);
        
        if (!criteriaList.isEmpty()) {
            Criteria[] criteriaArray = criteriaList.toArray(new Criteria[0]);
            query.addCriteria(new Criteria().andOperator(criteriaArray));
        }
        
        return mongoTemplate.find(query, PayrollEntry.class);
    }

    /**
     * Processa documentos da pessoa e aplica filtros.
     */
    private Flux<PayrollEntry> processPersonDocuments(
            Person person,
            List<Criteria> criteriaList,
            Query query,
            String rubricaCodigo,
            Integer ano,
            Integer mes,
            String origem,
            Double minValor,
            Double maxValor) {
        
        if (person.getDocumentos() == null || person.getDocumentos().isEmpty()) {
            return Flux.empty();
        }
        
        // Adiciona filtro de documentos da pessoa
        List<String> documentos = person.getDocumentos();
        if (documentos != null && !documentos.isEmpty()) {
            criteriaList.add(Criteria.where("documentoId").in(documentos));
        } else {
            // Pessoa sem documentos, retorna vazio
            return Flux.empty();
        }
        
        // Aplica outros filtros
        applyFilters(criteriaList, rubricaCodigo, ano, mes, origem, minValor, maxValor);
        
        if (!criteriaList.isEmpty()) {
            Criteria[] criteriaArray = criteriaList.toArray(new Criteria[0]);
            query.addCriteria(new Criteria().andOperator(criteriaArray));
        }
        
        return mongoTemplate.find(query, PayrollEntry.class);
    }

    /**
     * Aplica filtros comuns à lista de critérios.
     */
    private void applyFilters(List<Criteria> criteriaList,
                              String rubricaCodigo,
                              Integer ano,
                              Integer mes,
                              String origem,
                              Double minValor,
                              Double maxValor) {
        
        if (rubricaCodigo != null && !rubricaCodigo.isEmpty()) {
            criteriaList.add(Criteria.where("rubricaCodigo").is(rubricaCodigo));
        }
        
        // Filtro por ano e mês (tratamento especial quando ambos estão presentes)
        if (ano != null && mes != null) {
            // Se ambos estão presentes, busca exata (mais eficiente)
            String referenciaExata = String.format("%d-%02d", ano, mes);
            criteriaList.add(Criteria.where("referencia").is(referenciaExata));
        } else {
            // Filtro apenas por ano
            if (ano != null) {
                // Referência no formato YYYY-MM, então busca por regex
                String anoPattern = String.format("^%d-", ano);
                criteriaList.add(Criteria.where("referencia").regex(anoPattern));
            }
            
            // Filtro apenas por mês (sem ano)
            if (mes != null) {
                // Mes no formato MM (01-12), então busca por regex no final da string
                String mesPattern = String.format("-%02d$", mes);
                criteriaList.add(Criteria.where("referencia").regex(mesPattern));
            }
        }
        
        if (origem != null && !origem.isEmpty()) {
            criteriaList.add(Criteria.where("origem").is(origem));
        }
        
        if (minValor != null || maxValor != null) {
            Criteria valorCriteria = Criteria.where("valor");
            if (minValor != null) {
                valorCriteria.gte(minValor);
            }
            if (maxValor != null) {
                valorCriteria.lte(maxValor);
            }
            criteriaList.add(valorCriteria);
        }
    }
}

