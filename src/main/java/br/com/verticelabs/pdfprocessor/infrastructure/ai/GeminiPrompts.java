package br.com.verticelabs.pdfprocessor.infrastructure.ai;

/**
 * Prompts otimizados para extração de dados de documentos brasileiros usando Gemini 2.5.
 *
 * <p>Os prompts são estruturados para retornar JSON válido que pode ser
 * parseado diretamente para os DTOs do sistema.</p>
 *
 * <h3>Técnicas de Prompt Engineering aplicadas:</h3>
 * <ul>
 *   <li><strong>Precisão sobre completude</strong>: preferir null a adivinhar valores</li>
 *   <li><strong>Formato monetário explícito</strong>: ponto como decimal (1234.56)</li>
 *   <li><strong>Auto-verificação</strong>: modelo deve conferir somas antes de retornar</li>
 *   <li><strong>Contexto brasileiro</strong>: formatos de CPF, datas, valores em R$</li>
 *   <li><strong>JSON estrito</strong>: sem markdown, sem texto extra, apenas JSON</li>
 * </ul>
 *
 * <h3>Prompts Alternativos (para Cross-Validation — Fase 3):</h3>
 * <p>Cada tipo de documento possui um prompt alternativo ({@code _ALT}) que usa
 * uma abordagem diferente de extração. Na Fase 3, ambos são executados e os
 * resultados comparados campo a campo.</p>
 */
public final class GeminiPrompts {

    private GeminiPrompts() {
        // Utility class — não instanciar
    }

    // ==========================================
    // CONTRACHEQUE (Folha de Pagamento)
    // ==========================================

    /**
     * Prompt principal para extração de dados de contracheque.
     * Retorna JSON estruturado com dados do funcionário e rubricas.
     *
     * <p>Abordagem: extração top-down (visão geral → detalhes).</p>
     */
    public static final String CONTRACHEQUE_EXTRACTION = """
            Você é um sistema de extração de dados de alta precisão especializado em contracheques brasileiros.
            Analise esta imagem de contracheque e extraia TODOS os dados visíveis em formato JSON.

            REGRAS CRÍTICAS DE PRECISÃO:
            - Se um valor NÃO estiver claramente legível, retorne null. NUNCA adivinhe ou invente valores.
            - Valores monetários: use PONTO como decimal e SEM separador de milhar (ex: 5432.10, não 5.432,10)
            - Antes de retornar, VERIFIQUE: soma dos proventos deve ser igual ao salário bruto informado.
            - Antes de retornar, VERIFIQUE: salário bruto - total descontos = salário líquido.
            - Se as verificações falharem, revise os valores extraídos e corrija.
            - Retorne APENAS o JSON, sem texto adicional, sem markdown, sem explicações.

            FORMATO JSON OBRIGATÓRIO:
            {
              "nome": "nome completo do funcionário ou null",
              "cpf": "CPF no formato 000.000.000-00 ou null",
              "matricula": "número da matrícula ou null",
              "competencia": "mês/ano no formato MM/YYYY ou null",
              "cargo": "cargo ou função ou null",
              "departamento": "setor ou departamento ou null",
              "salarioBruto": 0.00,
              "totalDescontos": 0.00,
              "salarioLiquido": 0.00,
              "rubricas": [
                {
                  "codigo": "código da rubrica (ex: 001, 4482)",
                  "descricao": "descrição da rubrica",
                  "competencia": "MM/YYYY da rubrica se visível na coluna Competência/Prazo, ou null",
                  "referencia": 0.00,
                  "provento": 0.00,
                  "desconto": 0.00
                }
              ]
            }

            REGRAS DE EXTRAÇÃO:
            1. Proventos são créditos ao funcionário — campo "desconto" deve ser null
            2. Descontos são débitos do funcionário — campo "provento" deve ser null
            3. Referência pode ser horas, dias, percentual ou null
            4. Extraia TODAS as rubricas visíveis, incluindo as com valor zero
            5. O código da rubrica é o número que aparece antes da descrição (ex: 001, 101, 4482)
            6. Se o documento for da CAIXA ECONÔMICA FEDERAL ou FUNCEF, atente para os layouts específicos
            7. IMPORTANTE: Cada rubrica pode ter sua PRÓPRIA competência (coluna "Competência" ou "Prazo" na tabela).
               Se a competência da rubrica for DIFERENTE da competência do cabeçalho, inclua no campo "competencia" da rubrica.
               Isso acontece quando uma página tem rubricas de MESES DIFERENTES.
            """;

    /**
     * Prompt alternativo para cross-validation de contracheque (Fase 3).
     * Usa abordagem bottom-up (detalhes → visão geral).
     */
    public static final String CONTRACHEQUE_EXTRACTION_ALT = """
            Analise esta imagem de um documento de folha de pagamento brasileiro.

            INSTRUÇÕES PASSO A PASSO:
            1. Primeiro, identifique TODAS as linhas de rubrica visíveis no documento.
               Para cada linha, anote: código numérico, descrição, competência/prazo individual, referência, valor de provento, valor de desconto.
            2. Depois, identifique os totais: salário bruto, total descontos, salário líquido.
            3. Por último, identifique os dados pessoais: nome, CPF, matrícula, competência do cabeçalho, cargo.
            4. ATENÇÃO: cada rubrica pode ter sua PRÓPRIA competência na coluna "Competência" ou "Prazo".
               Se for diferente da competência do cabeçalho, inclua na rubrica.

            REGRAS:
            - Valores monetários com PONTO como decimal (ex: 1234.56)
            - Se algo não estiver legível, use null
            - Retorne APENAS JSON válido, sem texto extra

            JSON:
            {
              "nome": "string ou null",
              "cpf": "000.000.000-00 ou null",
              "matricula": "string ou null",
              "competencia": "MM/YYYY ou null",
              "cargo": "string ou null",
              "departamento": "string ou null",
              "salarioBruto": 0.00,
              "totalDescontos": 0.00,
              "salarioLiquido": 0.00,
              "rubricas": [
                {
                  "codigo": "string",
                  "descricao": "string",
                  "competencia": "MM/YYYY da rubrica ou null",
                  "referencia": null,
                  "provento": null,
                  "desconto": null
                }
              ]
            }
            """;

    // ==========================================
    // DECLARAÇÃO DE IMPOSTO DE RENDA (IRPF)
    // ==========================================

    /**
     * Prompt principal para extração de página de resumo de declaração de IR.
     * Retorna JSON com todos os campos fiscais extraídos.
     *
     * <p>Abordagem: extração por seções do RESUMO (Imposto Devido → Deduções → Imposto Pago → Resultado).</p>
     */
    public static final String IR_RESUMO_EXTRACTION = """
            Você é um sistema de extração de dados de alta precisão especializado em declarações de Imposto de Renda brasileiras (IRPF).
            Analise esta imagem da página RESUMO DA DECLARAÇÃO e extraia os dados em formato JSON.

            REGRAS CRÍTICAS DE PRECISÃO:
            - Se um valor NÃO estiver claramente legível, retorne null. NUNCA adivinhe ou invente valores.
            - Valores monetários: use PONTO como decimal e SEM separador de milhar (ex: 168097.04, não 168.097,04)
            - CPF no formato 000.000.000-00
            - ANTES de retornar, VERIFIQUE estas consistências:
              * Exercício deve ser Ano Calendário + 1 (ex: exercício 2024, ano calendário 2023)
              * Se há "Saldo de Imposto a Pagar", então "impostoRestituir" deve ser 0 ou null
              * Se há "Imposto a Restituir", então "saldoImpostoPagar" deve ser 0 ou null
              * A soma das deduções individuais deve ser aproximadamente igual ao total de deduções

            A PÁGINA DE RESUMO possui estas seções em ordem:
            1. DADOS BÁSICOS: nome, CPF, exercício, ano-calendário, tipo e modelo da declaração
            2. RENDIMENTOS TRIBUTÁVEIS: lista de fontes + TOTAL
            3. DEDUÇÕES: lista de tipos + TOTAL
            4. IMPOSTO DEVIDO: base de cálculo, imposto devido, deduções de incentivo, etc.
            5. IMPOSTO PAGO: retido na fonte, carnê-leão, complementar, etc. + TOTAL
            6. RESULTADO: saldo a pagar OU imposto a restituir

            FORMATO JSON OBRIGATÓRIO:
            {
              "exercicio": "ano do exercício (ex: 2024) ou null",
              "anoCalendario": "ano calendário (ex: 2023) ou null",
              "nome": "nome completo do declarante ou null",
              "cpf": "000.000.000-00 ou null",
              "tipoDeclaracao": "ORIGINAL ou RETIFICADORA ou null",
              "modeloDeclaracao": "COMPLETA ou SIMPLIFICADA ou null",
              "rendimentosTributaveis": 0.00,
              "deducoes": {
                "previdenciaOficial": 0.00,
                "previdenciaOficialRRA": 0.00,
                "previdenciaComplementar": 0.00,
                "dependentes": 0.00,
                "despesasMedicas": 0.00,
                "instrucao": 0.00,
                "pensaoAlimenticiaJudicial": 0.00,
                "pensaoAlimenticiaEscritura": 0.00,
                "pensaoAlimenticiaRRA": 0.00,
                "livrosCaixa": 0.00,
                "total": 0.00
              },
              "baseCalculoImposto": 0.00,
              "impostoDevido": 0.00,
              "deducaoIncentivo": 0.00,
              "impostoDevidoI": 0.00,
              "contribuicaoPrevEmpregadorDomestico": 0.00,
              "impostoDevidoII": 0.00,
              "impostoDevidoRRA": 0.00,
              "totalImpostoDevido": 0.00,
              "impostoPago": {
                "retidoFonteTitular": 0.00,
                "retidoFonteDependentes": 0.00,
                "carneLeaoTitular": 0.00,
                "carneLeaoDependentes": 0.00,
                "complementar": 0.00,
                "exterior": 0.00,
                "retidoFonteLei11033": 0.00,
                "retidoRRA": 0.00,
                "total": 0.00
              },
              "saldoImpostoPagar": 0.00,
              "impostoRestituir": 0.00,
              "descontoSimplificado": 0.00,
              "aliquotaEfetiva": 0.00
            }

            Retorne APENAS o JSON acima preenchido. Sem texto extra, sem markdown, sem explicações.
            """;

    /**
     * Prompt alternativo para cross-validation de IR (Fase 3).
     * Usa abordagem focada em seções individuais.
     */
    public static final String IR_RESUMO_EXTRACTION_ALT = """
            Analise esta imagem de uma página de RESUMO de declaração de Imposto de Renda brasileiro (IRPF).

            INSTRUÇÕES PASSO A PASSO:
            1. Identifique os DADOS DO CONTRIBUINTE: nome, CPF, exercício, ano-calendário.
            2. Localize a seção RENDIMENTOS TRIBUTÁVEIS e extraia o TOTAL.
            3. Localize a seção DEDUÇÕES e extraia cada item e o TOTAL.
            4. Localize a seção IMPOSTO DEVIDO e extraia todos os campos.
            5. Localize a seção IMPOSTO PAGO e extraia cada item e o TOTAL.
            6. Localize o RESULTADO: saldo a pagar ou imposto a restituir.

            REGRAS:
            - Valores monetários com PONTO como decimal (ex: 168097.04)
            - Se algo não estiver legível, use null
            - Retorne APENAS JSON válido

            JSON:
            {
              "exercicio": "string ou null",
              "anoCalendario": "string ou null",
              "nome": "string ou null",
              "cpf": "000.000.000-00 ou null",
              "rendimentosTributaveis": 0.00,
              "totalDeducoes": 0.00,
              "baseCalculoImposto": 0.00,
              "totalImpostoDevido": 0.00,
              "totalImpostoPago": 0.00,
              "saldoImpostoPagar": 0.00,
              "impostoRestituir": 0.00,
              "descontoSimplificado": 0.00,
              "aliquotaEfetiva": 0.00
            }
            """;

    // ==========================================
    // CONTRACHEQUE — MULTI-PAGE (páginas consecutivas)
    // ==========================================

    /**
     * Prompt para extração de contracheque que ocupa MAIS DE UMA PÁGINA.
     * Enviado junto com 2+ imagens de páginas consecutivas do PDF.
     *
     * <p>Cenário: o mês de Janeiro começa na página 1 e os descontos/totais
     * continuam na página 2. Ambas as imagens são enviadas e o Gemini deve
     * combinar os dados de ambas em um único JSON.</p>
     */
    public static final String CONTRACHEQUE_EXTRACTION_MULTIPAGE = """
            Você é um sistema de extração de dados de alta precisão especializado em contracheques brasileiros.
            Você está recebendo MÚLTIPLAS IMAGENS de PÁGINAS CONSECUTIVAS de um PDF de contracheque.

            CONTEXTO IMPORTANTE:
            - Estas páginas são CONSECUTIVAS de um mesmo documento.
            - Um contracheque pode começar em uma página e CONTINUAR na próxima (ex: proventos na pág 1, descontos na pág 2).
            - Uma mesma página pode conter o FINAL de um mês e o INÍCIO de outro mês.
            - Identifique TODOS os contracheques visíveis e extraia CADA UM como um objeto separado no array.

            REGRAS CRÍTICAS DE PRECISÃO:
            - Se um valor NÃO estiver claramente legível, retorne null. NUNCA adivinhe ou invente valores.
            - Valores monetários: use PONTO como decimal e SEM separador de milhar (ex: 5432.10, não 5.432,10)
            - Se uma rubrica tem descrição em MÚLTIPLAS LINHAS, concatene tudo na "descricao".
            - Retorne APENAS o JSON, sem texto adicional, sem markdown, sem explicações.

            FORMATO JSON OBRIGATÓRIO — array de contracheques:
            [
              {
                "nome": "nome completo do funcionário ou null",
                "cpf": "CPF no formato 000.000.000-00 ou null",
                "matricula": "número da matrícula ou null",
                "competencia": "mês/ano no formato MM/YYYY ou null",
                "cargo": "cargo ou função ou null",
                "departamento": "setor ou departamento ou null",
                "salarioBruto": 0.00,
                "totalDescontos": 0.00,
                "salarioLiquido": 0.00,
                "rubricas": [
                  {
                    "codigo": "código da rubrica (ex: 001, 4482)",
                    "descricao": "descrição da rubrica",
                    "competencia": "MM/YYYY se diferente do cabeçalho, ou null",
                    "referencia": 0.00,
                    "provento": 0.00,
                    "desconto": 0.00
                  }
                ]
              }
            ]

            REGRAS DE EXTRAÇÃO:
            1. Se houver DOIS contracheques diferentes (dois meses) visíveis, retorne DOIS objetos no array.
            2. Proventos são créditos ao funcionário — campo "desconto" deve ser null.
            3. Descontos são débitos do funcionário — campo "provento" deve ser null.
            4. Extraia TODAS as rubricas visíveis, incluindo as com valor zero.
            5. O código da rubrica é o número que aparece antes da descrição.
            6. Se o documento for da CAIXA ECONÔMICA FEDERAL ou FUNCEF, atente para os layouts específicos.
            7. Cada rubrica pode ter uma competência própria (campo "Competência" ou "Prazo"). Se presente, inclua no campo "competencia" da rubrica.
            """;

    /**
     * Prompt para extração de página PARCIAL de contracheque (continuação).
     * Usado quando uma página contém apenas a segunda metade de um contracheque
     * (descontos, totais) sem o cabeçalho completo.
     */
    public static final String CONTRACHEQUE_EXTRACTION_PARTIAL = """
            Você é um sistema de extração de dados de alta precisão especializado em contracheques brasileiros.
            Esta imagem é uma PÁGINA DE CONTINUAÇÃO de um contracheque — ela pode NÃO TER cabeçalho completo.

            CONTEXTO IMPORTANTE:
            - Esta página pode conter apenas DESCONTOS e TOTAIS (sem proventos e sem cabeçalho).
            - Pode conter o FINAL de um mês e o INÍCIO de outro mês na mesma página.
            - Se houver cabeçalho (nome, matrícula, empresa), extraia normalmente.
            - Se NÃO houver cabeçalho, retorne null nos campos de identificação.

            REGRAS CRÍTICAS:
            - Se um valor NÃO estiver legível, retorne null. NUNCA invente valores.
            - Valores monetários: PONTO como decimal, SEM separador de milhar.
            - Se uma rubrica tem descrição em MÚLTIPLAS LINHAS, concatene tudo na "descricao".
            - Retorne APENAS o JSON, sem texto adicional, sem markdown.

            FORMATO JSON OBRIGATÓRIO:
            {
              "nome": "nome ou null se não visível",
              "cpf": "CPF ou null",
              "matricula": "matrícula ou null",
              "competencia": "MM/YYYY ou null se não visível",
              "cargo": null,
              "departamento": null,
              "salarioBruto": 0.00,
              "totalDescontos": 0.00,
              "salarioLiquido": 0.00,
              "rubricas": [
                {
                  "codigo": "código da rubrica",
                  "descricao": "descrição da rubrica",
                  "competencia": "MM/YYYY se visível, ou null",
                  "referencia": 0.00,
                  "provento": 0.00,
                  "desconto": 0.00
                }
              ]
            }

            REGRAS DE EXTRAÇÃO:
            1. Extraia TODAS as rubricas visíveis, mesmo que a página pareça incompleta.
            2. Proventos: campo "desconto" = null. Descontos: campo "provento" = null.
            3. Se os totais (salário bruto, descontos, líquido) estiverem visíveis, extraia-os.
            4. Cada rubrica pode ter competência própria — se presente, inclua.
            5. O código da rubrica é o número antes da descrição (ex: 4346, 4412).
            """;

    // ==========================================
    // VALIDAÇÃO
    // ==========================================

    /**
     * Prompt para validação de dados extraídos de contracheque.
     * Recebe JSON com dados extraídos e retorna análise de consistência.
     *
     * <p>Usa {@code %s} como placeholder para o JSON dos dados extraídos.</p>
     */
    public static final String VALIDACAO_CONTRACHEQUE = """
            Você é um auditor especializado em folha de pagamento brasileira.
            Analise os dados extraídos do contracheque abaixo e verifique se há inconsistências.

            DADOS EXTRAÍDOS:
            %s

            VERIFICAÇÕES OBRIGATÓRIAS:
            1. A soma de todos os proventos (rubricas com campo "provento") é igual ao "salarioBruto"?
            2. A soma de todos os descontos (rubricas com campo "desconto") é igual ao "totalDescontos"?
            3. salarioBruto - totalDescontos = salarioLiquido?
            4. O CPF tem formato válido (000.000.000-00) e os dígitos verificadores estão corretos?
            5. A competência está no formato MM/YYYY com mês entre 01 e 13 (13 = abono anual)?
            6. Há rubricas com valores negativos (não deveria haver)?
            7. Há rubricas duplicadas (mesmo código)?

            Retorne APENAS este JSON:
            {
              "valido": true ou false,
              "inconsistencias": [
                {
                  "tipo": "SOMA_INCORRETA|CPF_INVALIDO|VALOR_NEGATIVO|DUPLICADO|FORMATO_INVALIDO",
                  "campo": "nome do campo com problema",
                  "valorEncontrado": "valor atual",
                  "valorEsperado": "valor calculado/esperado",
                  "mensagem": "descrição clara do problema"
                }
              ],
              "sugestoes": [
                "sugestão de correção 1"
              ]
            }
            """;

    // ==========================================
    // EXTRAÇÃO GENÉRICA DE TEXTO
    // ==========================================

    /**
     * Prompt para extração genérica de texto de PDF escaneado.
     * Retorna texto puro (não JSON) preservando a estrutura do documento.
     */
    public static final String EXTRACAO_TEXTO_GENERICO = """
            Extraia TODO o texto visível nesta imagem de documento com a máxima precisão possível.

            REGRAS:
            - Transcreva o texto EXATAMENTE como aparece no documento
            - Mantenha a estrutura e formatação original (quebras de linha, espaçamentos)
            - Preserve todos os números, datas, valores monetários e códigos
            - NÃO adicione interpretações, resumos ou comentários
            - NÃO corrija erros ortográficos do documento original
            - Se algo estiver ilegível, indique com [ilegível]

            Retorne apenas o texto extraído, sem JSON, sem markdown.
            """;
}
