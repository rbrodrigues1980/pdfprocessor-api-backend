package br.com.verticelabs.pdfprocessor.domain.tributacao;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo oficial de códigos de Pagamentos Efetuados e Doações Efetuadas (Receita Federal).
 */
public enum IrCodigoDeducao {

    // --- Instrução (LIMITADO_POR_CPF) ---
    C_01("01", "Instrução no Brasil", IrTipoRegraDeducao.LIMITADO_POR_CPF),
    C_02("02", "Instrução no exterior", IrTipoRegraDeducao.LIMITADO_POR_CPF),

    // --- Saúde (SEM_LIMITE) ---
    C_09("09", "Fonoaudiólogos no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_10("10", "Médicos no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_11("11", "Dentistas no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_12("12", "Psicólogos no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_13("13", "Fisioterapeutas no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_14("14", "Terapeutas ocupacionais no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_15("15", "Médicos no exterior", IrTipoRegraDeducao.SEM_LIMITE),
    C_16("16", "Dentistas no exterior", IrTipoRegraDeducao.SEM_LIMITE),
    C_17("17", "Psicólogos no exterior", IrTipoRegraDeducao.SEM_LIMITE),
    C_18("18", "Fisioterapeutas no exterior", IrTipoRegraDeducao.SEM_LIMITE),
    C_19("19", "Terapeutas ocupacionais no exterior", IrTipoRegraDeducao.SEM_LIMITE),
    C_20("20", "Fonoaudiólogos no exterior", IrTipoRegraDeducao.SEM_LIMITE),
    C_21("21", "Hospitais, clínicas e laboratórios no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_22("22", "Hospitais, clínicas e laboratórios no exterior", IrTipoRegraDeducao.SEM_LIMITE),
    C_26("26", "Planos de saúde no Brasil", IrTipoRegraDeducao.SEM_LIMITE),

    // --- Pensão alimentícia (SEM_LIMITE) ---
    C_30("30", "Pensão alimentícia judicial paga a residente no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_31("31", "Pensão alimentícia judicial paga a não residente no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_33("33", "Pensão alimentícia por escritura pública paga a residente no Brasil", IrTipoRegraDeducao.SEM_LIMITE),
    C_34("34", "Pensão alimentícia por escritura pública paga a não residente no Brasil", IrTipoRegraDeducao.SEM_LIMITE),

    // --- Previdência complementar (LIMITADO_RENDA_12PCT) ---
    C_36("36", "Previdência Complementar (PGBL)", IrTipoRegraDeducao.LIMITADO_RENDA_12PCT),
    C_37("37", "Previdência complementar fechada pública", IrTipoRegraDeducao.LIMITADO_RENDA_12PCT),

    // --- Doações (DEDUCAO_DIRETA_IMPOSTO) ---
    C_40("40", "Doações - ECA", IrTipoRegraDeducao.DEDUCAO_DIRETA_IMPOSTO),
    C_41("41", "Doações - Incentivo à Cultura", IrTipoRegraDeducao.DEDUCAO_DIRETA_IMPOSTO),
    C_42("42", "Doações - Incentivo ao Desporto", IrTipoRegraDeducao.DEDUCAO_DIRETA_IMPOSTO),
    C_43("43", "Doações - Estatuto do Idoso", IrTipoRegraDeducao.DEDUCAO_DIRETA_IMPOSTO),
    C_44("44", "Doações - PRONON", IrTipoRegraDeducao.DEDUCAO_DIRETA_IMPOSTO),
    C_45("45", "Doações - PRONAS/PCD", IrTipoRegraDeducao.DEDUCAO_DIRETA_IMPOSTO),

    // --- INSS empregador doméstico (TEMPORAL_DOMESTICO) ---
    C_50("50", "Contribuição patronal à Previdência Social — empregador doméstico", IrTipoRegraDeducao.TEMPORAL_DOMESTICO);

    private static final Map<String, IrCodigoDeducao> POR_CODIGO = Arrays.stream(values())
            .collect(Collectors.toMap(IrCodigoDeducao::getCodigo, Function.identity()));

    private final String codigo;
    private final String descricao;
    private final IrTipoRegraDeducao tipoRegra;

    IrCodigoDeducao(String codigo, String descricao, IrTipoRegraDeducao tipoRegra) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.tipoRegra = tipoRegra;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public IrTipoRegraDeducao getTipoRegra() {
        return tipoRegra;
    }

    public boolean isSaude() {
        return tipoRegra == IrTipoRegraDeducao.SEM_LIMITE
                && codigo.compareTo("09") >= 0
                && codigo.compareTo("26") <= 0;
    }

    public boolean isPensao() {
        return this == C_30 || this == C_31 || this == C_33 || this == C_34;
    }

    public boolean isIncentivoGlobal() {
        return this == C_40 || this == C_41 || this == C_42 || this == C_43;
    }

    /**
     * Resolve código oficial normalizado (ex.: "50", "050" → "50").
     */
    public static Optional<IrCodigoDeducao> fromCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String normalizado = normalizarCodigo(codigo);
        return Optional.ofNullable(POR_CODIGO.get(normalizado));
    }

    public static boolean isDesconhecido(String codigo) {
        return fromCodigo(codigo).isEmpty();
    }

    static String normalizarCodigo(String codigo) {
        String digits = codigo.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return codigo.trim();
        }
        int num = Integer.parseInt(digits);
        return String.format("%02d", num);
    }
}
