package br.com.verticelabs.pdfprocessor.interfaces.excel;

import br.com.verticelabs.pdfprocessor.application.excel.ResumoGeralUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralHonorariosResponse;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralLinhaResponse;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralResponse;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralTotaisResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelControllerResumoGeralTest {

    @Mock
    private br.com.verticelabs.pdfprocessor.application.excel.ExcelExportUseCase excelExportUseCase;

    @Mock
    private ResumoGeralUseCase resumoGeralUseCase;

    @InjectMocks
    private ExcelController excelController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(excelController).build();
    }

    @Test
    void getResumoGeral_retorna200ComPayload() {
        ResumoGeralResponse response = ResumoGeralResponse.builder()
                .nome("MARCIA REGINA")
                .cpf("44274378934")
                .atualizacao("SELIC RECEITA FEDERAL")
                .dataGeracao(LocalDateTime.now())
                .linhas(List.of(ResumoGeralLinhaResponse.builder()
                        .anoCalendario("2018")
                        .valorDeclaracao(new BigDecimal("1503.80"))
                        .valorSimulacao(new BigDecimal("4044.88"))
                        .principal(new BigDecimal("5548.68"))
                        .observacao("Impacto financeiro")
                        .build()))
                .totais(ResumoGeralTotaisResponse.builder()
                        .totalPrincipal(new BigDecimal("5548.68"))
                        .valorReceber(new BigDecimal("4882.84"))
                        .build())
                .honorarios(ResumoGeralHonorariosResponse.builder()
                        .label("Honorários Advocatícios - APCEF/SC - 12%")
                        .build())
                .build();

        when(resumoGeralUseCase.getByPersonId("person-1")).thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/persons/person-1/resumo-geral")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nome").isEqualTo("MARCIA REGINA")
                .jsonPath("$.linhas[0].anoCalendario").isEqualTo("2018")
                .jsonPath("$.linhas[0].valorSimulacao").isEqualTo(4044.88);
    }

    @Test
    void getResumoGeral_semDados_retorna204() {
        when(resumoGeralUseCase.getByPersonId("person-2")).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/persons/person-2/resumo-geral")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void getResumoGeral_pessoaNaoEncontrada_retorna404() {
        when(resumoGeralUseCase.getByPersonId("missing"))
                .thenReturn(Mono.error(new PersonNotFoundException("missing")));

        webTestClient.get()
                .uri("/persons/missing/resumo-geral")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
}
