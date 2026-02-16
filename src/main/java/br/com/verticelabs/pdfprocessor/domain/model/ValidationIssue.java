package br.com.verticelabs.pdfprocessor.domain.model;

/**
 * Representa um problema encontrado durante a validação dos dados extraídos.
 *
 * @param field    Nome do campo com problema (ex: "salarioBruto", "cpf")
 * @param type     Tipo do problema (ex: "SOMA_INCORRETA", "CPF_INVALIDO")
 * @param expected Valor esperado (calculado ou correto)
 * @param found    Valor encontrado na extração
 * @param message  Descrição legível do problema
 */
public record ValidationIssue(
        String field,
        String type,
        String expected,
        String found,
        String message
) {

    /**
     * Tipos de validação suportados.
     */
    public static final String TYPE_SOMA_PROVENTOS = "SOMA_PROVENTOS_INCORRETA";
    public static final String TYPE_SOMA_DESCONTOS = "SOMA_DESCONTOS_INCORRETA";
    public static final String TYPE_LIQUIDO_INCORRETO = "LIQUIDO_INCORRETO";
    public static final String TYPE_CPF_INVALIDO = "CPF_INVALIDO";
    public static final String TYPE_COMPETENCIA_INVALIDA = "COMPETENCIA_INVALIDA";
    public static final String TYPE_VALOR_NEGATIVO = "VALOR_NEGATIVO";
    public static final String TYPE_CAMPOS_OBRIGATORIOS = "CAMPOS_OBRIGATORIOS";
    public static final String TYPE_ANO_INCOERENTE = "ANO_INCOERENTE";
    public static final String TYPE_SOMA_DEDUCOES = "SOMA_DEDUCOES_INCORRETA";
    public static final String TYPE_RESULTADO_INCOERENTE = "RESULTADO_INCOERENTE";
    public static final String TYPE_SOMA_IMPOSTO_PAGO = "SOMA_IMPOSTO_PAGO_INCORRETA";
}
