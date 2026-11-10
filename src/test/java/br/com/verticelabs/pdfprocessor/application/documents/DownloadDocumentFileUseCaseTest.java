package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.DocumentNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.domain.service.StoredFile;
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

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownloadDocumentFileUseCase")
class DownloadDocumentFileUseCaseTest {

    @Mock
    PayrollDocumentRepository documentRepository;
    @Mock
    GridFsService gridFsService;

    @Test
    void usuarioDoTenantBaixaArquivoOriginal() {
        var useCase = new DownloadDocumentFileUseCase(documentRepository, gridFsService);
        PayrollDocument document = PayrollDocument.builder()
                .id("doc-1")
                .tenantId("tenant-1")
                .tipo(DocumentType.FUNCEF)
                .anoDetectado(2016)
                .originalFileId("507f1f77bcf86cd799439011")
                .build();
        when(documentRepository.findByTenantIdAndId("tenant-1", "doc-1")).thenReturn(Mono.just(document));
        when(gridFsService.openFile("507f1f77bcf86cd799439011"))
                .thenReturn(Mono.just(new StoredFile(
                        new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "contracheque-2016.pdf",
                        "application/pdf",
                        3)));

        StepVerifier.create(useCase.execute("doc-1").contextWrite(security("tenant-1", "TENANT_USER")))
                .assertNext(result -> {
                    assertEquals("contracheque-2016.pdf", result.fileName());
                    assertEquals("application/pdf", result.contentType());
                    assertEquals(3, result.sizeBytes());
                })
                .verifyComplete();
    }

    @Test
    void documentoSemArquivoNaoPodeSerBaixado() {
        var useCase = new DownloadDocumentFileUseCase(documentRepository, gridFsService);
        PayrollDocument document = PayrollDocument.builder()
                .id("doc-1")
                .tenantId("tenant-1")
                .tipo(DocumentType.INCOME_TAX)
                .anoDetectado(2017)
                .build();
        when(documentRepository.findByTenantIdAndId("tenant-1", "doc-1")).thenReturn(Mono.just(document));

        StepVerifier.create(useCase.execute("doc-1").contextWrite(security("tenant-1", "TENANT_USER")))
                .expectError(DocumentNotFoundException.class)
                .verify();
    }

    @Test
    void documentoDeOutroTenantNaoEEncontrado() {
        var useCase = new DownloadDocumentFileUseCase(documentRepository, gridFsService);
        when(documentRepository.findByTenantIdAndId("tenant-1", "doc-2")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("doc-2").contextWrite(security("tenant-1", "TENANT_USER")))
                .expectError(DocumentNotFoundException.class)
                .verify();
    }

    @Test
    void superAdminComTenantNoJwt_baixaDocumentoDeOutroTenant() {
        var useCase = new DownloadDocumentFileUseCase(documentRepository, gridFsService);
        PayrollDocument document = PayrollDocument.builder()
                .id("doc-2")
                .tenantId("outro-tenant")
                .tipo(DocumentType.FUNCEF)
                .anoDetectado(2016)
                .originalFileId("507f1f77bcf86cd799439011")
                .build();
        when(documentRepository.findById("doc-2")).thenReturn(Mono.just(document));
        when(gridFsService.openFile("507f1f77bcf86cd799439011"))
                .thenReturn(Mono.just(new StoredFile(
                        new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "contracheque-2016.pdf",
                        "application/pdf",
                        3)));

        StepVerifier.create(useCase.execute("doc-2").contextWrite(security("jwt-tenant", "SUPER_ADMIN")))
                .assertNext(result -> assertEquals("contracheque-2016.pdf", result.fileName()))
                .verifyComplete();
    }

    @Test
    void nomeDoArquivoRemoveCaminho() {
        assertEquals("informe.pdf", DownloadDocumentFileUseCase.safeFileName("C:\\pasta\\informe.pdf", "fallback.pdf"));
        assertEquals("fallback.pdf", DownloadDocumentFileUseCase.safeFileName("   ", "fallback.pdf"));
    }

    private static reactor.util.context.Context security(String tenantId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user-1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(tenantId);
        return ReactiveSecurityContextHolder.withAuthentication(auth);
    }
}
