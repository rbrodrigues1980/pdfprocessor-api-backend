package br.com.verticelabs.pdfprocessor.domain.tributacao;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IrCodigoDeducaoTest {

    @Test
    void fromCodigo_normalizaZeros() {
        assertEquals(Optional.of(IrCodigoDeducao.C_50), IrCodigoDeducao.fromCodigo("50"));
        assertEquals(Optional.of(IrCodigoDeducao.C_50), IrCodigoDeducao.fromCodigo("050"));
        assertEquals(Optional.of(IrCodigoDeducao.C_01), IrCodigoDeducao.fromCodigo("1"));
    }

    @Test
    void tipoRegra_saudeSemLimite() {
        assertEquals(IrTipoRegraDeducao.SEM_LIMITE, IrCodigoDeducao.C_26.getTipoRegra());
        assertTrue(IrCodigoDeducao.C_26.isSaude());
    }

    @Test
    void tipoRegra_educacaoLimitadoPorCpf() {
        assertEquals(IrTipoRegraDeducao.LIMITADO_POR_CPF, IrCodigoDeducao.C_01.getTipoRegra());
    }

    @Test
    void tipoRegra_pgbl12Pct() {
        assertEquals(IrTipoRegraDeducao.LIMITADO_RENDA_12PCT, IrCodigoDeducao.C_36.getTipoRegra());
    }

    @Test
    void tipoRegra_domesticoTemporal() {
        assertEquals(IrTipoRegraDeducao.TEMPORAL_DOMESTICO, IrCodigoDeducao.C_50.getTipoRegra());
    }

    @Test
    void tipoRegra_doacoes() {
        assertEquals(IrTipoRegraDeducao.DEDUCAO_DIRETA_IMPOSTO, IrCodigoDeducao.C_40.getTipoRegra());
        assertTrue(IrCodigoDeducao.C_41.isIncentivoGlobal());
        assertFalse(IrCodigoDeducao.C_44.isIncentivoGlobal());
    }

    @Test
    void isDesconhecido() {
        assertTrue(IrCodigoDeducao.isDesconhecido("99"));
        assertFalse(IrCodigoDeducao.isDesconhecido("50"));
    }
}
