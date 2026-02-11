package br.com.verticelabs.pdfprocessor.application.consolidation;

import br.com.verticelabs.pdfprocessor.application.entries.EntryQueryUseCase;
import br.com.verticelabs.pdfprocessor.application.rubricas.RubricaUseCase;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class ConsolidationUseCaseTest {

        @Mock
        private PayrollDocumentRepository documentRepository;

        @Mock
        private EntryQueryUseCase entryQueryUseCase;

        @Mock
        private PersonRepository personRepository;

        @Mock
        private RubricaUseCase rubricaUseCase;

        @Mock
        private ReferenceNormalizer referenceNormalizer;

        @InjectMocks
        private ConsolidationUseCase consolidationUseCase;

        @Test
        public void testConsolidationPrecision() {
                // Arrange
                String cpf = "123.456.789-00";
                String tenantId = "tenant1";
                String documentId = "doc1";

                Person person = new Person();
                person.setCpf(cpf);
                person.setNome("Test Person");
                person.setTenantId(tenantId);
                person.setDocumentos(List.of(documentId));

                PayrollDocument doc = new PayrollDocument();
                doc.setId(documentId);
                doc.setTenantId(tenantId);
                doc.setMesesDetectados(List.of("01", "02")); // Simulating doc covers these months

                // Scenario creating floating point error in Double: 0.1 + 0.2 =
                // 0.30000000000000004
                PayrollEntry entry1 = PayrollEntry.builder()
                                .id("entry1")
                                .documentoId(documentId)
                                .rubricaCodigo("R001")
                                .rubricaDescricao("Rubrica Teste")
                                .referencia("2024-01")
                                .valor(new BigDecimal("0.1"))
                                .build();

                PayrollEntry entry2 = PayrollEntry.builder()
                                .id("entry2")
                                .documentoId(documentId)
                                .rubricaCodigo("R001")
                                .rubricaDescricao("Rubrica Teste")
                                .referencia("2024-02")
                                .valor(new BigDecimal("0.2"))
                                .build();

                // Setup Mocks
                Mockito.when(personRepository.findByTenantIdAndCpf(tenantId, cpf))
                                .thenReturn(Mono.just(person));

                Rubrica rubrica = new Rubrica();
                rubrica.setCodigo("R001");
                Mockito.when(rubricaUseCase.listarAtivas())
                                .thenReturn(Flux.just(rubrica));

                Mockito.when(entryQueryUseCase.findByCpf(cpf, tenantId))
                                .thenReturn(Flux.just(entry1, entry2));

                Mockito.when(documentRepository.findByTenantIdAndId(tenantId, documentId))
                                .thenReturn(Mono.just(doc));

                // Mock ReferenceNormalizer logic
                Mockito.when(referenceNormalizer.isValid(Mockito.anyString())).thenReturn(true);
                Mockito.when(referenceNormalizer.extractYear(Mockito.anyString())).thenReturn("2024");
                // For normalize call in buildConsolidatedResponse loop (if isValid returns
                // true, normalize might not be called or needed)
                // But the code calls normalize if !isValid, OR if it IS valid it puts it in
                // entryToNormalizedRef.
                // Wait, line 286: if isValid -> normalized = trimmedRef.
                // So I just need isValid to return true for "2024-01" and "2024-02".

                // Act
                ConsolidatedResponse response = consolidationUseCase.consolidate(cpf, tenantId, null, null).block();

                // Assert
                Assertions.assertNotNull(response);
                Assertions.assertFalse(response.getRubricas().isEmpty());
                ConsolidationRow row = response.getRubricas().get(0);
                Assertions.assertEquals("R001", row.getCodigo());

                // Check total. In Double this might fail if not handled carefully, but with
                // BigDecimal it should be exactly 0.3
                BigDecimal expectedTotal = new BigDecimal("0.3");
                Assertions.assertEquals(0, expectedTotal.compareTo(row.getTotal()), "Total should be exactly 0.3");

                // Verify matrix values - keys are references YYYY-MM
                Assertions.assertEquals(0, new BigDecimal("0.1").compareTo(row.getValores().get("2024-01")));
                Assertions.assertEquals(0, new BigDecimal("0.2").compareTo(row.getValores().get("2024-02")));
        }
}
