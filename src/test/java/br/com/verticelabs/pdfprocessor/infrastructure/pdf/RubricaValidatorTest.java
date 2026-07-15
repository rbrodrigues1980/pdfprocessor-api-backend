package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RubricaValidator - código + descrição obrigatórios")
class RubricaValidatorTest {

    @Mock
    private RubricaRepository rubricaRepository;

    @InjectMocks
    private RubricaValidator rubricaValidator;

    @Test
    @DisplayName("3396: aceita quando código e descrição batem")
    void aceita3396ComDescricaoCorreta() {
        Rubrica rubrica = Rubrica.builder()
                .codigo("3396")
                .descricao("REP TAXA ADMINISTRATIVA BUA NOVO PLANO")
                .ativo(true)
                .build();
        when(rubricaRepository.findByCodigo("3396")).thenReturn(Mono.just(rubrica));

        StepVerifier.create(rubricaValidator.validateRubrica(
                        "3396", "REP TAXA ADMINISTRATIVA BUA NOVO PLANO"))
                .expectNext(rubrica)
                .verifyComplete();
    }

    @Test
    @DisplayName("3396: rejeita quando descrição diverge")
    void rejeita3396ComDescricaoDivergente() {
        Rubrica rubrica = Rubrica.builder()
                .codigo("3396")
                .descricao("REP TAXA ADMINISTRATIVA BUA NOVO PLANO")
                .ativo(true)
                .build();
        when(rubricaRepository.findByCodigo("3396")).thenReturn(Mono.just(rubrica));

        StepVerifier.create(rubricaValidator.validateRubrica(
                        "3396", "REP TAXA ADMINISTRATIVA BUA"))
                .verifyComplete();
    }

    @Test
    @DisplayName("4432: aceita FUNCEF CONTR. EQUACIONAMENTO2 SALDADO")
    void aceita4432ComDescricaoCorreta() {
        Rubrica rubrica = Rubrica.builder()
                .codigo("4432")
                .descricao("FUNCEF CONTRIB EQU SALDADO 02")
                .ativo(true)
                .build();
        when(rubricaRepository.findByCodigo("4432")).thenReturn(Mono.just(rubrica));

        StepVerifier.create(rubricaValidator.validateRubrica(
                        "4432", "FUNCEF CONTR. EQUACIONAMENTO2 SALDADO"))
                .expectNext(rubrica)
                .verifyComplete();
    }

    @Test
    @DisplayName("4432: rejeita descrição de outra rubrica FUNCEF")
    void rejeita4432ComDescricaoDivergente() {
        Rubrica rubrica = Rubrica.builder()
                .codigo("4432")
                .descricao("FUNCEF CONTRIB EQU SALDADO 02")
                .ativo(true)
                .build();
        when(rubricaRepository.findByCodigo("4432")).thenReturn(Mono.just(rubrica));

        StepVerifier.create(rubricaValidator.validateRubrica(
                        "4432", "FUNCEF CONTRIB EQU SALDADO 02"))
                .verifyComplete();
    }

    @Test
    @DisplayName("4436: aceita quando código e descrição batem")
    void aceita4436ComDescricaoCorreta() {
        Rubrica rubrica = Rubrica.builder()
                .codigo("4436")
                .descricao("FUNCEF CONTRIB EQU SALDADO 02 GRT NATAL")
                .ativo(true)
                .build();
        when(rubricaRepository.findByCodigo("4436")).thenReturn(Mono.just(rubrica));

        StepVerifier.create(rubricaValidator.validateRubrica(
                        "4436", "FUNCEF CONTRIB EQU SALDADO 02 GRT NATAL"))
                .expectNext(rubrica)
                .verifyComplete();
    }

    @Test
    @DisplayName("4436: rejeita quando descrição diverge")
    void rejeita4436ComDescricaoDivergente() {
        Rubrica rubrica = Rubrica.builder()
                .codigo("4436")
                .descricao("FUNCEF CONTRIB EQU SALDADO 02 GRT NATAL")
                .ativo(true)
                .build();
        when(rubricaRepository.findByCodigo("4436")).thenReturn(Mono.just(rubrica));

        StepVerifier.create(rubricaValidator.validateRubrica(
                        "4436", "FUNCEF CONTR. EQUACIONAMENTO2 SALDADO"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Outras rubricas: valida só por código (descrição não obriga)")
    void outrasRubricasSoPorCodigo() {
        Rubrica rubrica = Rubrica.builder()
                .codigo("4482")
                .descricao("CONTRIBUIÇÃO EXTRAORDINÁRIA ABONO ANUAL 2015")
                .ativo(true)
                .build();
        when(rubricaRepository.findByCodigo("4482")).thenReturn(Mono.just(rubrica));

        StepVerifier.create(rubricaValidator.validateRubrica(
                        "4482", "QUALQUER TEXTO EXTRAIDO"))
                .expectNext(rubrica)
                .verifyComplete();
    }

    @Test
    @DisplayName("3396: aceita com variação de espaços/caixa")
    void aceita3396ComNormalizacao() {
        Rubrica rubrica = Rubrica.builder()
                .codigo("3396")
                .descricao("REP TAXA ADMINISTRATIVA BUA NOVO PLANO")
                .ativo(true)
                .build();
        when(rubricaRepository.findByCodigo("3396")).thenReturn(Mono.just(rubrica));

        StepVerifier.create(rubricaValidator.validateRubrica(
                        "3396", "  rep taxa administrativa bua novo plano  "))
                .expectNext(rubrica)
                .verifyComplete();
    }
}
