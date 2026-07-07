package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver;
import br.com.verticelabs.pdfprocessor.application.excel.ResumoGeralMontagemResult;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumoGeralPdfGeneratorTest {

    private final ResumoGeralPdfGenerator generator = new ResumoGeralPdfGenerator();

    @Test
    void generate_produzPdfValidoComUmaLinha() {
        Person person = Person.builder()
                .cpf("09850279249")
                .nome("Margarida Mika Watanabe")
                .build();

        ExcelResumoGeralLinhaDTO linha = ExcelResumoGeralLinhaDTO.builder()
                .anoCalendario("2016")
                .valorDeclaracao(new BigDecimal("13272.54"))
                .valorSimulacao(new BigDecimal("12982.61"))
                .principal(new BigDecimal("289.93"))
                .selicAcumulada(new BigDecimal("80.04"))
                .valorCorrecao(new BigDecimal("232.06"))
                .principalMaisCorrecao(new BigDecimal("521.99"))
                .observacao(ExcelResumoGeralHelper.OBS_IMPACTO)
                .build();

        var honor = new EmpresaHonorariosResolver.HonorariosConfig(
                new BigDecimal("0.12"), new BigDecimal("12.00"), "APCEF", "APCEF PA", null);
        var totais = new ExcelResumoGeralHelper.TotaisResumoGeral(
                new BigDecimal("289.93"),
                new BigDecimal("232.06"),
                new BigDecimal("521.99"),
                new BigDecimal("62.64"),
                new BigDecimal("459.35"));

        ResumoGeralMontagemResult montagem = new ResumoGeralMontagemResult(
                List.of(linha),
                honor,
                totais,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                LocalDate.now(),
                LocalDateTime.of(2026, 6, 24, 20, 26));

        byte[] pdf = generator.generate(person, montagem);

        assertTrue(pdf.length > 500);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
        assertTrue(generator.buildFilename(person).endsWith(".pdf"));
    }
}
