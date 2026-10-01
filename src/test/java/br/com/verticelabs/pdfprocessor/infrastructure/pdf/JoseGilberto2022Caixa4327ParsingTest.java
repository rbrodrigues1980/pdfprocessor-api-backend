package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contracheque Caixa APCEF BA 2022 (José Gilberto): 4327 IMPOSTO DE RENDA - DEP JUDICIAL.
 * Não confundir com a 4327 da FUNCEF (IMPOSTO RENDA FONTE).
 */
@DisplayName("Caixa 4327 DEP JUDICIAL José Gilberto 2022")
class JoseGilberto2022Caixa4327ParsingTest {

    private static final String LINHAS_CAIXA = """
            4325 IMPOSTO DE RENDA 999 4.641,4501/2022
            4327 IMPOSTO DE RENDA - DEP JUDICIAL 999 677,8301/2022
            4327 IMPOSTO DE RENDA - DEP JUDICIAL 365,9111/2022
            4327 IMPOSTO DE RENDA - DEP JUDICIAL 999 707,1011/2022
            4326 IMPOSTO DE RENDA - GRAT NATAL 5.622,1312/2022
            """;

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());

    @Test
    @DisplayName("extrai 4327 com descrição e valores do contracheque Caixa")
    void extrai4327DepJudicialDoContrachequeCaixa() {
        List<PdfLineParser.ParsedLine> parsed = parser.parseLines(LINHAS_CAIXA, DocumentType.CAIXA);
        List<PdfLineParser.ParsedLine> linhas4327 = parsed.stream()
                .filter(p -> "4327".equals(p.getCodigo()))
                .toList();

        assertEquals(3, linhas4327.size());
        assertTrue(linhas4327.stream().allMatch(p ->
                "IMPOSTO DE RENDA - DEP JUDICIAL".equalsIgnoreCase(p.getDescricao().trim())));
        assertEquals("677,83", linhas4327.get(0).getValorStr());
        assertEquals("01/2022", linhas4327.get(0).getReferencia());
        assertTrue(linhas4327.stream().anyMatch(p -> "365,91".equals(p.getValorStr())));
        assertTrue(linhas4327.stream().anyMatch(p -> "707,10".equals(p.getValorStr())));
    }
}
