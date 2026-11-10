package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteDocumentUseCase")
class DeleteDocumentUseCaseTest {

    @Mock
    PayrollDocumentRepository documentRepository;
    @Mock
    PayrollEntryRepository entryRepository;
    @Mock
    PersonRepository personRepository;
    @Mock
    GridFsService gridFsService;

    @Test
    @DisplayName("SUPER_ADMIN com tenantId no JWT exclui documento de outro tenant")
    void superAdminComTenantNoJwt_excluiDocumentoDeOutroTenant() {
        var useCase = new DeleteDocumentUseCase(
                documentRepository, entryRepository, personRepository, gridFsService);

        String jwtTenant = "897e342e-a0d7-4655-aa3f-1d07ce13e5bc";
        String docTenant = "outro-tenant-id";
        String documentId = "doc-cross-tenant";
        String fileId = "507f1f77bcf86cd799439011";
        String cpf = "12345678901";

        PayrollDocument document = PayrollDocument.builder()
                .id(documentId)
                .tenantId(docTenant)
                .cpf(cpf)
                .tipo(DocumentType.SABESP)
                .status(DocumentStatus.PROCESSED)
                .originalFileId(fileId)
                .build();

        Person person = Person.builder()
                .id("person-1")
                .tenantId(docTenant)
                .cpf(cpf)
                .documentos(new ArrayList<>(List.of(documentId, "outro-doc")))
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Mono.just(document));
        when(entryRepository.deleteByTenantIdAndDocumentoId(docTenant, documentId)).thenReturn(Mono.empty());
        when(gridFsService.deleteFile(fileId)).thenReturn(Mono.empty());
        when(personRepository.findByTenantIdAndCpf(docTenant, cpf)).thenReturn(Mono.just(person));
        when(personRepository.save(any(Person.class))).thenReturn(Mono.just(person));
        when(documentRepository.deleteByTenantIdAndId(docTenant, documentId)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(documentId)
                        .contextWrite(security(jwtTenant, "SUPER_ADMIN")))
                .verifyComplete();

        verify(documentRepository).findById(documentId);
        verify(documentRepository, never()).findByTenantIdAndId(any(), any());
        verify(entryRepository).deleteByTenantIdAndDocumentoId(docTenant, documentId);
        verify(gridFsService).deleteFile(fileId);
        verify(documentRepository).deleteByTenantIdAndId(docTenant, documentId);
    }

    private static Context security(String tenantId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin-1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(tenantId);
        return ReactiveSecurityContextHolder.withAuthentication(auth);
    }
}
