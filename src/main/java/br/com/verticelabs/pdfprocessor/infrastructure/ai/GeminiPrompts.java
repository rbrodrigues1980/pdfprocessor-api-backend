package br.com.verticelabs.pdfprocessor.infrastructure.ai;

/**
 * Prompts otimizados para extração de dados de documentos usando Gemini AI.
 * 
 * Os prompts são estruturados para retornar JSON válido que pode ser
 * parseado diretamente para os DTOs do sistema.
 */
public final class GeminiPrompts {

    private GeminiPrompts() {
        // Utility class
    }

    /**
     * Prompt para extração de dados de contracheque.
     * Retorna JSON estruturado com dados do funcionário e rubricas.
     */
    public static final String CONTRACHEQUE_EXTRACTION = """
            Você é um especialista em extração de dados de contracheques brasileiros.
            Analise esta imagem de contracheque e extraia TODOS os dados em formato JSON.

            IMPORTANTE:
            - Analise CUIDADOSAMENTE cada linha do contracheque
            - Identifique TODAS as rubricas (proventos e descontos)
            - Valores monetários devem ser números decimais (sem R$, pontos de milhar, use ponto como decimal)
            - Use null para campos não encontrados
            - Retorne APENAS o JSON, sem texto adicional, sem markdown

            Formato esperado:
            {
              "nome": "nome completo do funcionário",
              "cpf": "CPF no formato 000.000.000-00",
              "matricula": "número da matrícula",
              "competencia": "mês/ano no formato MM/YYYY",
              "cargo": "cargo ou função",
              "departamento": "setor ou departamento",
              "salarioBruto": 0.00,
              "totalDescontos": 0.00,
              "salarioLiquido": 0.00,
              "rubricas": [
                {
                  "codigo": "código da rubrica (ex: 001, 101)",
                  "descricao": "descrição da rubrica",
                  "referencia": 0.00,
                  "provento": 0.00,
                  "desconto": 0.00
                }
              ]
            }

            REGRAS:
            1. Proventos são valores positivos (créditos ao funcionário)
            2. Descontos são valores a serem subtraídos
            3. Se uma rubrica é provento, o campo "desconto" deve ser null
            4. Se uma rubrica é desconto, o campo "provento" deve ser null
            5. Referência pode ser horas, dias, percentual ou null
            6. Extraia TODAS as rubricas visíveis, mesmo as com valor zero
            """;

    /**
     * Prompt para extração de página de resumo de declaração de IR.
     */
    public static final String IR_RESUMO_EXTRACTION = """
            Você é um especialista em extração de dados de declarações de Imposto de Renda brasileiras.
            Analise esta imagem da página RESUMO da declaração e extraia os dados em formato JSON.

            IMPORTANTE:
            - Esta é a página de RESUMO DA DECLARAÇÃO
            - Valores monetários devem ser números decimais (sem R$, use ponto como decimal)
            - Use null para campos não encontrados
            - Retorne APENAS o JSON, sem texto adicional, sem markdown

            Formato esperado:
            {
              "exercicio": "ano do exercício (ex: 2024)",
              "anoCalendario": "ano calendário (ex: 2023)",
              "nome": "nome completo do declarante",
              "cpf": "CPF no formato 000.000.000-00",
              "tipoDeclaracao": "ORIGINAL ou RETIFICADORA",
              "modeloDeclaracao": "COMPLETA ou SIMPLIFICADA",
              "rendimentosTributaveis": 0.00,
              "deducoes": {
                "previdenciaOficial": 0.00,
                "previdenciaComplementar": 0.00,
                "dependentes": 0.00,
                "despesasMedicas": 0.00,
                "instrucao": 0.00,
                "pensaoAlimenticia": 0.00,
                "livrosCaixaRural": 0.00,
                "total": 0.00
              },
              "baseCalculoImposto": 0.00,
              "impostoDevido": 0.00,
              "deducaoIncentivo": 0.00,
              "impostoDevidoAposDeducao": 0.00,
              "impostoPago": {
                "retidoFonte": 0.00,
                "carneLean": 0.00,
                "complementar": 0.00,
                "exterior": 0.00,
                "total": 0.00
              },
              "saldoImpostoPagar": 0.00,
              "impostoRestituir": 0.00,
              "valorQuota": 0.00,
              "numeroParcelas": 0
            }

            REGRAS:
            1. Se há "Saldo de Imposto a Pagar", impostoRestituir deve ser null ou 0
            2. Se há "Imposto a Restituir", saldoImpostoPagar deve ser null ou 0
            3. Exercício é o ano de entrega (ex: 2024 para IR 2024)
            4. Ano Calendário é o ano base (ex: 2023 para IR 2024)
            """;

    /**
     * Prompt para validação de dados extraídos de contracheque.
     */
    public static final String VALIDACAO_CONTRACHEQUE = """
            Você é um auditor especializado em folha de pagamento.
            Analise os dados extraídos do contracheque e verifique inconsistências.

            Dados extraídos:
            %s

            Verifique:
            1. A soma dos proventos corresponde ao salário bruto?
            2. A soma dos descontos corresponde ao total de descontos?
            3. Salário Bruto - Total Descontos = Salário Líquido?
            4. Os valores parecem realistas para um contracheque brasileiro?
            5. Há rubricas duplicadas ou com valores suspeitos?

            Retorne JSON:
            {
              "valido": true/false,
              "inconsistencias": [
                {
                  "tipo": "SOMA_INCORRETA|VALOR_SUSPEITO|DUPLICADO|OUTRO",
                  "campo": "nome do campo com problema",
                  "valorEncontrado": "valor atual",
                  "valorEsperado": "valor calculado/esperado",
                  "mensagem": "descrição do problema"
                }
              ],
              "sugestoes": [
                "sugestão de correção 1",
                "sugestão de correção 2"
              ]
            }
            """;

    /**
     * Prompt para extração genérica de texto de PDF escaneado.
     */
    public static final String EXTRACAO_TEXTO_GENERICO = """
            Extraia TODO o texto visível nesta imagem de documento.

            REGRAS:
            - Mantenha a estrutura e formatação original
            - Preserve quebras de linha
            - Inclua números, datas e valores
            - Não adicione interpretações, apenas o texto exato

            Retorne apenas o texto extraído, sem JSON.
            """;
}
