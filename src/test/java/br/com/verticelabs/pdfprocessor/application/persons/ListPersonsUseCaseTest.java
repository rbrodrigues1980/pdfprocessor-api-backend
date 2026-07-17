package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.application.security.EvaluatorAccessService;
import br.com.verticelabs.pdfprocessor.domain.model.PersonStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListPersonsUseCase - filtros empresa e cadastro")
class ListPersonsUseCaseTest {

    private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @Mock
    private EvaluatorAccessService evaluatorAccessService;

    private ListPersonsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListPersonsUseCase(mongoTemplate, evaluatorAccessService);
    }

    @Test
    @DisplayName("startOfDay usa início do dia em America/Sao_Paulo")
    void startOfDayUsaFusoBrasil() {
        LocalDate date = LocalDate.of(2026, 6, 19);
        Instant expected = date.atStartOfDay(ZONE_BR).toInstant();
        assertEquals(expected, ListPersonsUseCase.startOfDay(date));
    }

    @Test
    @DisplayName("startOfNextDay deixa cadastroAte inclusivo")
    void startOfNextDayDeixaAteInclusivo() {
        LocalDate date = LocalDate.of(2026, 7, 14);
        Instant expected = date.plusDays(1).atStartOfDay(ZONE_BR).toInstant();
        assertEquals(expected, ListPersonsUseCase.startOfNextDay(date));
    }

    @Test
    @DisplayName("filtra por empresaId")
    void filtraPorEmpresaId() {
        Query query = useCase.buildQuery(null, new ListPersonsFilters(
                null, null, null, null, "emp-123", null, null, null));

        String queryStr = query.toString();
        assertTrue(queryStr.contains("empresaId"));
        assertTrue(queryStr.contains("emp-123"));
    }

    @Test
    @DisplayName("filtra apenas cadastroDe (aberto)")
    void filtraApenasCadastroDe() {
        Query query = new Query();
        ListPersonsUseCase.applyCreatedAtRange(query, LocalDate.of(2026, 6, 1), null);

        String queryStr = query.toString();
        assertTrue(queryStr.contains("createdAt"));
        assertTrue(queryStr.contains("$gte"));
    }

    @Test
    @DisplayName("filtra apenas cadastroAte (aberto e inclusivo)")
    void filtraApenasCadastroAte() {
        Query query = new Query();
        ListPersonsUseCase.applyCreatedAtRange(query, null, LocalDate.of(2026, 7, 14));

        String queryStr = query.toString();
        assertTrue(queryStr.contains("createdAt"));
        assertTrue(queryStr.contains("$lt"));
    }

    @Test
    @DisplayName("filtra intervalo completo de cadastro")
    void filtraIntervaloCompleto() {
        Query query = useCase.buildQuery("tenant-1", new ListPersonsFilters(
                null,
                null,
                null,
                null,
                "emp-1",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 14),
                null));

        String queryStr = query.toString();
        assertTrue(queryStr.contains("tenantId"));
        assertTrue(queryStr.contains("empresaId"));
        assertTrue(queryStr.contains("createdAt"));
        assertTrue(queryStr.contains("$gte"));
        assertTrue(queryStr.contains("$lt"));
    }

    @Test
    @DisplayName("combina empresaId com validado=false")
    void combinaEmpresaComValidado() {
        Query query = useCase.buildQuery("tenant-1", new ListPersonsFilters(
                null, null, null, false, "emp-9", null, null, null));

        String queryStr = query.toString();
        assertTrue(queryStr.contains("empresaId"));
        assertTrue(queryStr.contains("validado"));
    }

    @Test
    @DisplayName("filtra por status FINALIZADO")
    void filtraPorStatusFinalizado() {
        Query query = useCase.buildQuery(null, new ListPersonsFilters(
                null, null, null, null, null, null, null, PersonStatus.FINALIZADO));

        String queryStr = query.toString();
        assertTrue(queryStr.contains("status"));
        assertTrue(queryStr.contains("FINALIZADO"));
        assertFalse(queryStr.contains("$or"));
    }

    @Test
    @DisplayName("filtra EM_PROCESSAMENTO incluindo status nulo/ausente via $nin")
    void filtraEmProcessamentoIncluiNulo() {
        Query query = useCase.buildQuery(null, new ListPersonsFilters(
                null, null, null, null, null, null, null, PersonStatus.EM_PROCESSAMENTO));

        String queryStr = query.toString();
        assertTrue(queryStr.contains("status"));
        assertTrue(queryStr.contains("$nin"));
        assertTrue(queryStr.contains("FINALIZADO"));
        assertFalse(queryStr.contains("$or"));
    }

    @Test
    @DisplayName("combina validado=false com status EM_PROCESSAMENTO sem conflito de $or")
    void combinaValidadoFalseComEmProcessamento() {
        Query query = useCase.buildQuery(null, new ListPersonsFilters(
                null, null, null, false, null, null, null, PersonStatus.EM_PROCESSAMENTO));

        String queryStr = query.toString();
        assertTrue(queryStr.contains("validado"));
        assertTrue(queryStr.contains("status"));
        assertTrue(queryStr.contains("$nin"));
        assertFalse(queryStr.contains("$or"));
    }

    @Test
    @DisplayName("buildQuery sem filtros não aplica status nem validado")
    void buildQuerySemFiltros() {
        Query query = useCase.buildQuery(null, new ListPersonsFilters(
                null, null, null, null, null, null, null, null));

        String queryStr = query.toString();
        assertFalse(queryStr.contains("validado"));
        assertFalse(queryStr.contains("status"));
        assertFalse(queryStr.contains("empresaId"));
    }
}
