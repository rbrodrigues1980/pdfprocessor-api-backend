package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Seletor genérico Original/Retificadora (agnóstico a nº de páginas)")
class IrpfEntregaVigenteSelectorTest {

    @Test
    @DisplayName("Retificadora no início (Iracema): prevalece sobre Original no fim")
    void retificadoraNoInicioVence() {
        String texto = """
                === PAGINA 1 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Tipo de declaração: Declaração de Ajuste Anual Retificadora
                Data/Hora da Entrega: 07/02/2022 às 10:00:00
                RESUMO
                Total do imposto devido 46.075,29
                === PAGINA 2 ===
                PAGAMENTOS EFETUADOS
                === PAGINA 9 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Tipo de declaração: Declaração de Ajuste Anual Original
                Data/Hora da Entrega: 25/05/2021 às 08:00:00
                RESUMO
                Total do imposto devido 39.286,60
                === PAGINA 10 ===
                PAGAMENTOS EFETUADOS
                """;

        var vigente = IrpfEntregaVigenteSelector.selecionarVigente(texto).orElseThrow();
        assertTrue(vigente.retificadora());
        assertEquals(1, vigente.startPage());
        assertEquals(2, vigente.endPage());
        assertTrue(vigente.text().contains("46.075,29"));
        assertFalse(vigente.text().contains("39.286,60"));
    }

    @Test
    @DisplayName("Retificadora no fim (Fátima): ainda prevalece")
    void retificadoraNoFimVence() {
        String texto = """
                === PAGINA 1 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Tipo de declaração: Declaração de Ajuste Anual Original
                Data/Hora da Entrega: 01/04/2025 às 10:00:00
                RESUMO
                Despesas médicas 33.816,04
                === PAGINA 13 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Tipo de declaração: Declaração de Ajuste Anual Retificadora
                Data/Hora da Entrega: 15/05/2025 às 12:00:00
                RESUMO
                Despesas médicas 21.319,04
                """;

        String vigente = IrpfEntregaVigenteSelector.recortarDeclaracaoVigente(texto);
        assertTrue(vigente.contains("21.319,04"));
        assertFalse(vigente.contains("33.816,04"));
    }

    @Test
    @DisplayName("Blocos com tamanhos diferentes: Original curta + Retificadora longa")
    void blocosDesiguaisNaoDependemDeNumeroDePaginas() {
        String texto = """
                === PAGINA 1 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Tipo de declaração: Declaração de Ajuste Anual Original
                Data/Hora da Entrega: 01/01/2020 às 09:00:00
                RESUMO
                Total do imposto devido 10.000,00
                === PAGINA 2 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Tipo de declaração: Declaração de Ajuste Anual Retificadora
                Data/Hora da Entrega: 02/02/2021 às 11:00:00
                === PAGINA 3 ===
                página extra da retificadora
                === PAGINA 4 ===
                RESUMO
                Total do imposto devido 22.222,22
                === PAGINA 5 ===
                mais uma página
                """;

        var vigente = IrpfEntregaVigenteSelector.selecionarVigente(texto).orElseThrow();
        assertTrue(vigente.retificadora());
        assertEquals(2, vigente.startPage());
        assertEquals(5, vigente.endPage());
        assertEquals(4, vigente.resumoPage());
        assertTrue(vigente.text().contains("22.222,22"));
        assertFalse(vigente.text().contains("10.000,00"));
    }

    @Test
    @DisplayName("Sem tipo: empate resolve pela data mais recente")
    void semTipoUsaData() {
        String texto = """
                === PAGINA 1 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Data/Hora da Entrega: 10/01/2020 às 10:00:00
                RESUMO
                A
                === PAGINA 2 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Data/Hora da Entrega: 20/02/2021 às 10:00:00
                RESUMO
                B
                """;

        var vigente = IrpfEntregaVigenteSelector.selecionarVigente(texto).orElseThrow();
        assertTrue(vigente.text().contains("B"));
    }

    @Test
    @DisplayName("devePreferir: Retificadora vence Original em docs separados")
    void devePreferirRetificadora() {
        assertTrue(IrpfEntregaVigenteSelector.devePreferir(
                "Retificadora", "07/02/2022 às 10:00:00",
                "Original", "25/05/2021 às 08:00:00"));
        assertFalse(IrpfEntregaVigenteSelector.devePreferir(
                "Original", "25/05/2021 às 08:00:00",
                "Retificadora", "07/02/2022 às 10:00:00"));
    }

    @Test
    @DisplayName("detectarBlocos: N blocos com tamanhos variáveis")
    void detectarBlocosTamanhosVariaveis() {
        String texto = """
                === PAGINA 1 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Tipo de declaração: Original
                === PAGINA 2 ===
                RESUMO
                === PAGINA 3 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                Tipo de declaração: Retificadora
                === PAGINA 4 ===
                mid
                === PAGINA 5 ===
                RESUMO
                """;
        List<IrpfEntregaVigenteSelector.EntregaBloco> blocos =
                IrpfEntregaVigenteSelector.detectarBlocos(texto);
        assertEquals(2, blocos.size());
        assertEquals(1, blocos.get(0).startPage());
        assertEquals(2, blocos.get(0).endPage());
        assertEquals(3, blocos.get(1).startPage());
        assertEquals(5, blocos.get(1).endPage());
    }
}
