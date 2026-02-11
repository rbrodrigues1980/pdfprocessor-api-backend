package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoPayrollDocumentRepositoryAdapter implements PayrollDocumentRepository {

    private final SpringDataPayrollDocumentRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<PayrollDocument> save(PayrollDocument document) {
        return repository.save(document);
    }

    // Métodos legados
    @Override
    @Deprecated
    public Mono<PayrollDocument> findById(String id) {
        return repository.findById(id);
    }

    @Override
    @Deprecated
    public Flux<PayrollDocument> findAll() {
        return repository.findAll();
    }

    @Override
    @Deprecated
    public Flux<PayrollDocument> findByStatus(DocumentStatus status) {
        return repository.findByStatus(status);
    }

    @Override
    @Deprecated
    public Flux<PayrollDocument> findByCpf(String cpf) {
        return repository.findByCpf(cpf);
    }

    @Override
    @Deprecated
    public Mono<PayrollDocument> findByFileHash(String fileHash) {
        return repository.findByFileHash(fileHash);
    }

    @Override
    @Deprecated
    public Mono<Void> deleteById(String id) {
        return repository.deleteById(id);
    }

    @Override
    public Mono<PayrollDocument> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    @Override
    public Flux<PayrollDocument> findAllByTenantId(String tenantId) {
        return repository.findAllByTenantId(tenantId);
    }

    @Override
    public Flux<PayrollDocument> findByTenantIdAndStatus(String tenantId, DocumentStatus status) {
        return repository.findByTenantIdAndStatus(tenantId, status);
    }

    @Override
    public Flux<PayrollDocument> findByTenantIdAndCpf(String tenantId, String cpf) {
        return repository.findByTenantIdAndCpf(tenantId, cpf);
    }

    @Override
    public Mono<PayrollDocument> findByTenantIdAndFileHash(String tenantId, String fileHash) {
        return repository.findByTenantIdAndFileHash(tenantId, fileHash);
    }

    @Override
    public Mono<Void> deleteByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .flatMap(repository::delete)
                .then();
    }

    @Override
    public Mono<Long> countByTenantId(String tenantId) {
        return repository.countByTenantId(tenantId);
    }

    @Override
    public Mono<Long> countAll() {
        return repository.count();
    }

    @Override
    public Flux<PayrollDocument> findByTenantIdAndFilters(
            String tenantId,
            String cpf,
            Integer ano,
            DocumentStatus status,
            DocumentType tipo,
            Long minEntries,
            Long maxEntries) {
        
        Query query = new Query();
        
        // Sempre filtrar por tenantId
        query.addCriteria(Criteria.where("tenantId").is(tenantId));
        
        if (cpf != null && !cpf.trim().isEmpty()) {
            query.addCriteria(Criteria.where("cpf").is(cpf));
        }
        
        if (ano != null) {
            query.addCriteria(Criteria.where("anoDetectado").is(ano));
        }
        
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        
        if (tipo != null) {
            query.addCriteria(Criteria.where("tipo").is(tipo));
        }
        
        if (minEntries != null) {
            query.addCriteria(Criteria.where("totalEntries").gte(minEntries));
        }
        
        if (maxEntries != null) {
            query.addCriteria(Criteria.where("totalEntries").lte(maxEntries));
        }
        
        // Ordenar por data de upload (mais recente primeiro)
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "dataUpload"));
        
        return mongoTemplate.find(query, PayrollDocument.class);
    }

    @Override
    @Deprecated
    public Flux<PayrollDocument> findByFilters(
            String cpf,
            Integer ano,
            DocumentStatus status,
            DocumentType tipo,
            Long minEntries,
            Long maxEntries) {
        
        Query query = new Query();
        
        if (cpf != null && !cpf.trim().isEmpty()) {
            query.addCriteria(Criteria.where("cpf").is(cpf));
        }
        
        if (ano != null) {
            query.addCriteria(Criteria.where("anoDetectado").is(ano));
        }
        
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        
        if (tipo != null) {
            query.addCriteria(Criteria.where("tipo").is(tipo));
        }
        
        if (minEntries != null) {
            query.addCriteria(Criteria.where("totalEntries").gte(minEntries));
        }
        
        if (maxEntries != null) {
            query.addCriteria(Criteria.where("totalEntries").lte(maxEntries));
        }
        
        // Ordenar por data de upload (mais recente primeiro)
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "dataUpload"));
        
        return mongoTemplate.find(query, PayrollDocument.class);
    }
}

