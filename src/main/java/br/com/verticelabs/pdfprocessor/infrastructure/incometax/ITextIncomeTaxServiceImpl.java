package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.ITextIncomeTaxService;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.DependenteInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.DoacaoEfetuada;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.FontePagadora;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementação do serviço de extração de declarações de IR usando iText 8.
 * Usa LocationTextExtractionStrategy para melhor extração de PDFs com layouts
 * complexos.
 */
@Slf4j
@Service
public class ITextIncomeTaxServiceImpl implements ITextIncomeTaxService {

    // ==========================================
    // PADRÕES REGEX PARA EXTRAÇÃO
    // ==========================================

    // Dados Básicos
    // Nota: usar '.' para letras acentuadas (ex: á/Á, í/Í) porque CASE_INSENSITIVE
    // do Java só faz case-fold ASCII — sem UNICODE_CASE, 'í' não casa com 'Í'.
    private static final Pattern ANO_CALENDARIO_PATTERN = Pattern.compile(
            "(?i)ano[\\s.-]*calend.rio[\\s:]*([\\d]{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern EXERCICIO_PATTERN = Pattern.compile(
            "(?i)exerc.cio[\\s:]*([\\d]{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern NOME_PATTERN = Pattern.compile(
            "(?i)nome[\\s:]+([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇa-záéíóúàèìòùâêîôûãõç\\s]+?)(?=\\s*(?:CPF|\\d{3}\\.\\d{3}|$))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);

    private static final Pattern CPF_PATTERN = Pattern.compile(
            "(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // IMPOSTO DEVIDO
    private static final Pattern BASE_CALCULO_IMPOSTO_PATTERN = Pattern.compile(
            "(?i)base\\s+de\\s+c[aá]lculo\\s+do\\s+imposto[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_DEVIDO_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido(?![\\s]*(I|II|RRA))[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DEDUCAO_INCENTIVO_PATTERN = Pattern.compile(
            "(?i)dedu[çc][ãa]o\\s+de\\s+incentivo[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_DEVIDO_I_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+I(?![I])[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+prev[\\s\\S]*?empregador\\s+dom[eé]stico[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_DEVIDO_II_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+II[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_DEVIDO_RRA_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+RRA[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern TOTAL_IMPOSTO_DEVIDO_PATTERN = Pattern.compile(
            "(?i)total\\s+do\\s+imposto\\s+devido[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern SALDO_IMPOSTO_PAGAR_PATTERN = Pattern.compile(
            "(?i)saldo\\s+(?:de\\s+)?imposto\\s+a\\s+pagar[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Rendimentos e Deduções
    // Padrão para capturar o TOTAL de RENDIMENTOS TRIBUTÁVEIS
    // Texto: "TOTAL\r\n168.097,04" após a seção RENDIMENTOS TRIBUTÁVEIS
    // 'TRIBUT.VEIS' cobre TRIBUTÁVEIS (Á maiúsculo não é casado por CASE_INSENSITIVE sem UNICODE_CASE)
    private static final Pattern RENDIMENTOS_TRIBUTAVEIS_TOTAL_PATTERN = Pattern.compile(
            "(?i)RENDIMENTOS\\s+TRIBUT.VEIS[\\s\\S]*?TOTAL[\\r\\n\\s]+([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Padrão alternativo: buscar pela última ocorrência de TOTAL na seção
    private static final Pattern RENDIMENTOS_TRIBUTAVEIS_PATTERN = Pattern.compile(
            "(?i)(?:total\\s+de\\s+)?rendimentos\\s+tribut.veis[^\\d]*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // 'DEDU..ES' cobre DEDUÇÕES (Ç e Õ maiúsculos)
    private static final Pattern DEDUCOES_TOTAL_PATTERN = Pattern.compile(
            "(?i)DEDU..ES[\\s\\S]*?TOTAL[\\r\\n\\s]+([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_RETIDO_FONTE_TITULAR_PATTERN = Pattern.compile(
            "(?i)imposto\\s+retido\\s+na\\s+fonte\\s+do\\s+titular[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_PAGO_TOTAL_PATTERN = Pattern.compile(
            "(?i)total\\s+do\\s+imposto\\s+pago[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_RESTITUIR_PATTERN = Pattern.compile(
            "(?i)imposto\\s+a\\s+restituir[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // DEDUÇÕES Individuais
    //
    // OFICIAL cobre dois layouts:
    //   • SIMPLIFICADO / antigo: "Contribuição à previdência oficial"
    //   • DEDUÇÕES LEGAIS:       "Contribuições às previdências oficial e complementar aberta ou
    //                             fechada de que trata o § 15 do art. 40 da CF/1988
    //                             (até o limite do patrocinador)"
    //
    // 'contribui[çc].(?:es|o)' casa tanto com "contribuição" (ã+o) quanto com "contribuições" (õ+es).
    // A negative-lookahead '(?!\\s*\\(?[Rr]endimentos)' impede casar com a linha RRA logo abaixo.
    private static final Pattern DEDUCOES_CONTRIB_PREV_OFICIAL_PATTERN = Pattern.compile(
            "(?i)contribui[çc].(?:es|o)\\s+[àa]s?\\s+previd[êe]ncias?\\s+oficial(?!\\s*\\(?[Rr]endimentos)[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // RRA cobre dois formatos do label:
    //   • antigo:       "Contribuição à previdência oficial (RRA)"
    //   • DEDUÇÕES LEGAIS: "Contribuição à previdência oficial (Rendimentos recebidos acumuladamente)"
    private static final Pattern DEDUCOES_CONTRIB_PREV_RRA_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+[àa]\\s+previd[êe]ncia\\s+oficial\\s*\\(?(?:RRA|[Rr]endimentos\\s+recebidos\\s+acumuladamente)\\)?[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // PGBL/prev. privada dedutível — linha "acima do limite do patrocinador" ou "privada, e Fapi"
    private static final Pattern DEDUCOES_CONTRIB_PREV_COMPL_ACIMA_LIMITE_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+[àa]\\s+(?:previd[êe]ncia|prev\\.)\\s+complementar[\\s\\S]*?"
                    + "(?:acima\\s+do\\s+limite|privada|\\bFapi\\b)[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Fallback genérico — exclui linha "até o limite do patrocinador" (prev. oficial + compl. pública)
    private static final Pattern DEDUCOES_CONTRIB_PREV_COMPL_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+[àa]\\s+(?:previd[êe]ncia|prev\\.)\\s+complementar"
                    + "(?!\\s+p[úu]blica\\s*\\([^)]*at[ée]\\s+o\\s+limite)[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DEDUCOES_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DEDUCOES_INSTRUCAO_PATTERN = Pattern.compile(
            "(?i)despesas\\s+com\\s+instru[çc][ãa]o[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DEDUCOES_MEDICAS_PATTERN = Pattern.compile(
            "(?i)despesas\\s+m[eé]dicas[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DEDUCOES_PENSAO_JUDICIAL_PATTERN = Pattern.compile(
            "(?i)pens[ãa]o\\s+aliment[íi]cia\\s+judicial(?!\\s*\\()?[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DEDUCOES_PENSAO_ESCRITURA_PATTERN = Pattern.compile(
            "(?i)pens[ãa]o\\s+aliment[íi]cia\\s+por\\s+escritura[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // RRA cobre dois formatos:
    //   • antigo:          "Pensão alimentícia judicial (RRA)"
    //   • DEDUÇÕES LEGAIS: "Pensão alimentícia judicial (Rendimentos recebidos acumuladamente)"
    private static final Pattern DEDUCOES_PENSAO_RRA_PATTERN = Pattern.compile(
            "(?i)pens[ãa]o\\s+aliment[íi]cia\\s+judicial\\s*\\(?(?:RRA|[Rr]endimentos\\s+recebidos\\s+acumuladamente)\\)?[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DEDUCOES_LIVRO_CAIXA_PATTERN = Pattern.compile(
            "(?i)livro\\s+caixa[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // IMPOSTO PAGO Individuais
    private static final Pattern IMPOSTO_RETIDO_FONTE_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)imp\\.?\\s+retido\\s+na\\s+fonte\\s+dos\\s+dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern CARNE_LEAO_TITULAR_PATTERN = Pattern.compile(
            "(?i)carn[êe]-?le[ãa]o\\s+do\\s+titular[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern CARNE_LEAO_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)carn[êe]-?le[ãa]o\\s+dos\\s+dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_COMPLEMENTAR_PATTERN = Pattern.compile(
            "(?i)imposto\\s+complementar[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_PAGO_EXTERIOR_PATTERN = Pattern.compile(
            "(?i)imposto\\s+pago\\s+no\\s+exterior[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_RETIDO_FONTE_LEI_11033_PATTERN = Pattern.compile(
            "(?i)imposto\\s+retido\\s+na\\s+fonte\\s*\\(?Lei[\\s\\S]*?11\\.?033[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_RETIDO_RRA_PATTERN = Pattern.compile(
            "(?i)imposto\\s+retido\\s+RRA[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Campos 2017+ (Desconto Simplificado — ver extractDescontoSimplificadoResumo)
    private static final Pattern ALIQUOTA_EFETIVA_PATTERN = Pattern.compile(
            "(?i)al.quota\\s+efetiva[\\s\\S]*?([\\d]{1,3},\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ==========================================
    // NOVOS PADRÕES — IDENTIFICAÇÃO E RESUMO
    // ==========================================

    // Tipo de tributação — detectar o cabeçalho do RESUMO
    // Usa '.' para acentos e UNICODE_CASE para garantir case-insensitive com Unicode
    private static final Pattern TIPO_TRIBUTACAO_SIMPLIFICADO_PATTERN = Pattern.compile(
            "TRIBUTA..O\\s+UTILIZANDO\\s+O\\s+DESCONTO\\s+SIMPLIFICADO",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern TIPO_TRIBUTACAO_COMPLETO_PATTERN = Pattern.compile(
            "(?:TRIBUTA..O\\s+(?:COM|UTILIZANDO\\s+AS)\\s+DEDU..ES\\s+LEGAIS|DEDU..ES\\s+LEGAIS)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Dados de identificação (página 1)
    private static final Pattern DATA_NASCIMENTO_PATTERN = Pattern.compile(
            "(?i)data\\s+de\\s+nascimento[\\s:]*([\\d]{2}/[\\d]{2}/[\\d]{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // 't.tulo' cobre Título/TÍTULO — UNICODE_CASE para 'í'/'Í'
    private static final Pattern TITULO_ELEITORAL_PATTERN = Pattern.compile(
            "(?i)t.tulo\\s+eleitoral[\\s:]*([\\d]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // 'declara..o' cobre declaração/DECLARAÇÃO
    private static final Pattern TIPO_DECLARACAO_PATTERN = Pattern.compile(
            "(?i)tipo\\s+de\\s+declara..o[\\s:\\r\\n]*((?:declara..o|retifica)[^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DATA_ENTREGA_PATTERN = Pattern.compile(
            "(?i)data/hora\\s+da\\s+entrega[\\s:]*([\\d]{2}/[\\d]{2}/[\\d]{4}\\s+.s\\s+[\\d]{2}:[\\d]{2}:[\\d]{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Número de controle da declaração gerado pela Receita Federal. */
    private static final Pattern CONTROLE_PATTERN = Pattern.compile(
            "(?i)controle[:\\s]*(\\d{10,20})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Evolução patrimonial — dois valores para bens (anterior e atual)
    private static final Pattern BENS_DIREITOS_PATTERN = Pattern.compile(
            "(?i)bens\\s+e\\s+direitos\\s+em\\s+31/12/\\d{4}[\\s\\r\\n]+([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);

    // 'd.vidas' cobre dívidas/DÍVIDAS; '.nus' cobre ônus/ÔNUS
    private static final Pattern DIVIDAS_ONUS_PATTERN = Pattern.compile(
            "(?i)d.vidas\\s+e\\s+.nus\\s+reais\\s+em\\s+31/12/\\d{4}[\\s\\r\\n]+([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);

    // Outras informações do RESUMO
    // 'n.o' cobre não/NÃO; 'tribut.veis' cobre tributáveis/TRIBUTÁVEIS
    private static final Pattern RENDIMENTOS_ISENTOS_PATTERN = Pattern.compile(
            "(?i)rendimentos\\s+isentos\\s+e\\s+n.o\\s+tribut.veis[\\s\\r\\n]+([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);

    // 'tributa..o' cobre tributação/TRIBUTAÇÃO
    private static final Pattern RENDIMENTOS_TRIB_EXCLUSIVA_PATTERN = Pattern.compile(
            "(?i)rendimentos\\s+sujeitos\\s+.\\s+tributa..o\\s+exclusiva[^\\d\\r\\n]*[\\r\\n]+([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);

    // Pagamentos efetuados — captura código, nome, cnpj/cpf, valor (layout PDF: cada dado em linha separada)
    private static final Pattern PAGAMENTOS_SECTION_PATTERN = Pattern.compile(
            "(?i)PAGAMENTOS\\s+EFETUADOS[\\s\\S]*?(?=DOAS?[ÕO]ES|DECLARA[ÇC][ÃA]O\\s+DE\\s+BENS|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ==========================================
    // PADRÕES — LINHAS INDIVIDUAIS DE RENDIMENTOS TRIBUTÁVEIS (RESUMO)
    // ==========================================

    private static final Pattern REND_PJ_TITULAR_PATTERN = Pattern.compile(
            "(?i)recebidos\\s+de\\s+pessoa\\s+jur.dica\\s+pelo\\s+titular[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern REND_PJ_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)recebidos\\s+de\\s+pessoa\\s+jur.dica\\s+pelos\\s+dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern REND_PF_TITULAR_PATTERN = Pattern.compile(
            "(?i)recebidos\\s+de\\s+pessoa\\s+f.sica.exterior\\s+pelo\\s+titular[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern REND_PF_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)recebidos\\s+de\\s+pessoa\\s+f.sica.exterior\\s+pelos\\s+dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern RESULTADO_ATIVIDADE_RURAL_PATTERN = Pattern.compile(
            "(?i)resultado\\s+tribut.vel\\s+da\\s+atividade\\s+rural[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern REND_ACUMULADOS_TITULAR_PATTERN = Pattern.compile(
            "(?i)recebidos\\s+acumuladamente\\s+pelo\\s+titular[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern REND_ACUMULADOS_DEPENDENTES_PATTERN = Pattern.compile(
            "(?i)recebidos\\s+acumuladamente\\s+pelos\\s+dependentes[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ==========================================
    // PADRÕES — OUTRAS INFORMAÇÕES (RESUMO)
    // ==========================================

    private static final Pattern IMPOSTO_PAGO_GANHOS_CAPITAL_PATTERN = Pattern.compile(
            "(?i)imposto\\s+pago\\s+sobre\\s+ganhos\\s+de\\s+capital(?!\\s+moeda)[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_DEVIDO_GANHOS_CAPITAL_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+sobre\\s+ganhos\\s+de\\s+capital(?!\\s+moeda|\\s+l.quidos)[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_DEVIDO_GANHOS_CAPITAL_MOEDA_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+sobre\\s+ganhos\\s+de\\s+capital\\s+moeda\\s+estrangeira[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_PAGO_GANHOS_CAPITAL_MOEDA_PATTERN = Pattern.compile(
            "(?i)imposto\\s+pago\\s+(?:sobre\\s+)?ganhos?\\s+de\\s+capital\\s+moeda\\s+estrangeira[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_PAGO_RENDA_VARIAVEL_PATTERN = Pattern.compile(
            "(?i)imposto\\s+pago\\s+sobre\\s+renda\\s+vari.vel[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_DEVIDO_GANHOS_LIQUIDOS_RV_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+sobre\\s+ganhos\\s+l.quidos[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_PAGAR_GANHO_CAPITAL_MOEDA_ESPECIE_PATTERN = Pattern.compile(
            "(?i)imposto\\s+a\\s+pagar\\s+sobre\\s+o\\s+ganho\\s+de\\s+capital[\\s\\S]*?esp.cie[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern REND_TRIBUTAVEIS_EXIG_SUSPENSA_PATTERN = Pattern.compile(
            "(?i)rendimentos\\s+tribut.veis[^\\n]*exigibilidade\\s+suspensa[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DEPOSITOS_JUDICIAIS_PATTERN = Pattern.compile(
            "(?i)dep.sitos\\s+judiciais\\s+do\\s+imposto[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern IMPOSTO_DIFERIDO_GANHOS_CAPITAL_PATTERN = Pattern.compile(
            "(?i)imposto\\s+diferido\\s+dos\\s+ganhos\\s+de\\s+capital[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DOACOES_PARTIDOS_POLITICOS_PATTERN = Pattern.compile(
            "(?i)doa..es\\s+a\\s+partidos\\s+pol.ticos[\\s\\S]*?([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ==========================================
    // PADRÕES — FONTES PAGADORAS (RENDIMENTOS PJ PELO TITULAR)
    // ==========================================

    /** Identifica páginas de "RENDIMENTOS TRIBUTÁVEIS RECEBIDOS DE PESSOA JURÍDICA PELO TITULAR". */
    private static final Pattern FONTE_PAGADORA_PAGE_PATTERN = Pattern.compile(
            "RENDIMENTOS\\s+TRIBUT.VEIS\\s+RECEBIDOS\\s+DE\\s+PESSOA\\s+JUR.DICA\\s+PELO\\s+TITULAR",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Nome da fonte pagadora — linha logo após o label "NOME DA FONTE PAGADORA". */
    private static final Pattern FONTE_PAGADORA_NOME_PATTERN = Pattern.compile(
            "(?i)NOME\\s+DA\\s+FONTE\\s+PAGADORA[\\s:]*([A-Z\\u00C0-\\u00FF][^\\n\\r]{2,80})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** CNPJ ou CPF da fonte pagadora. */
    private static final Pattern FONTE_PAGADORA_CNPJ_PATTERN = Pattern.compile(
            "(?i)CNPJ/CPF[:\\s]*([\\d]{2}\\.[\\d]{3}\\.[\\d]{3}/[\\d]{4}-[\\d]{2}|[\\d]{3}\\.[\\d]{3}\\.[\\d]{3}-[\\d]{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Contribuição previdência oficial (INSS / FUNCEF) na página de rendimentos PJ. */
    private static final Pattern FONTE_PAGADORA_CONTRIB_PREV_PATTERN = Pattern.compile(
            "(?i)CONTR\\.?\\s+PREVID\\.?(?:\\s+OFICIAL)?[^\\d]*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Rendimento tributável na página de rendimentos PJ. */
    private static final Pattern FONTE_PAGADORA_RENDIMENTO_PATTERN = Pattern.compile(
            "(?i)RENDIMENTO\\s+TRIBUT.VEL[^\\d]*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Imposto retido na fonte na página de rendimentos PJ. */
    private static final Pattern FONTE_PAGADORA_IRRF_PATTERN = Pattern.compile(
            "(?i)IMPOSTO\\s+RETIDO\\s+NA\\s+FONTE[^\\d]*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Linha de pagamento multilinha: código, nome, CNPJ/CPF, [NIT opcional], valor pago, [parcNaoDedutivel]
    private static final Pattern PAGAMENTO_ENTRY_PATTERN = Pattern.compile(
            "(?m)^\\s*(\\d{1,3})\\s*\\n([^\\n]{3,80})\\n([\\d]{2,3}\\.[\\d]{3}\\.[\\d]{3}[/-][\\d]{4}-[\\d]{2}|[\\d]{3}\\.[\\d]{3}\\.[\\d]{3}-[\\d]{2})\\s*\\n" +
            "(?:([\\d]{3}\\.[\\d]{5}\\.[\\d]{2}-[\\d]|\\d{11})\\s*\\n)?" +
            "([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})(?:\\n([\\d]{1,3}(?:[.]\\d{3})*,\\d{2}))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);

    // Linha inline (SERPRO): "50 NOME CPF/CNPJ [NIT] VALOR [PARC]"
    private static final Pattern PAGAMENTO_INLINE_PATTERN = Pattern.compile(
            "(?m)^\\s*(\\d{1,3})\\s+(.+?)\\s+" +
            "([\\d]{2,3}\\.[\\d]{3}\\.[\\d]{3}/[\\d]{4}-[\\d]{2}|[\\d]{3}\\.[\\d]{3}\\.[\\d]{3}-[\\d]{2})\\s+" +
            "(?:([\\d]{3}\\.[\\d]{5}\\.[\\d]{2}-[\\d]|\\d{11})\\s+)?" +
            "([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})\\s*" +
            "([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);

    @Override
    public Mono<IncomeTaxDeclarationService.IncomeTaxInfo> extractIncomeTaxInfo(InputStream inputStream) {
        log.info("🚀 Iniciando extração de IR com iText 8");

        return Mono.fromCallable(() -> {
            byte[] pdfBytes = inputStream.readAllBytes();
            inputStream.close();
            return pdfBytes;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(pdfBytes -> {
                    return findResumoPage(new ByteArrayInputStream(pdfBytes))
                            .flatMap(resumoPage -> {
                                log.info("📄 Página RESUMO encontrada: {}", resumoPage);

                                return Mono.zip(
                                        extractRawTextFromPage(new ByteArrayInputStream(pdfBytes), 1),
                                        extractRawTextFromPage(new ByteArrayInputStream(pdfBytes), resumoPage),
                                        extractAllPagesText(new ByteArrayInputStream(pdfBytes)))
                                        .map(tuple -> {
                                            String primeiraPageText = tuple.getT1();
                                            String resumoPageText = tuple.getT2();
                                            String allPagesText = tuple.getT3();

                                            log.debug("📝 Texto primeira página (primeiros 500 chars): {}",
                                                    primeiraPageText.substring(0,
                                                            Math.min(500, primeiraPageText.length())));
                                            log.debug("📝 Texto página RESUMO (primeiros 500 chars): {}",
                                                    resumoPageText.substring(0,
                                                            Math.min(500, resumoPageText.length())));

                                            return parseIncomeTaxInfo(primeiraPageText, resumoPageText, allPagesText);
                                        });
                            });
                })
                .doOnSuccess(info -> log.info("✅ Extração concluída com sucesso"))
                .doOnError(e -> log.error("❌ Erro na extração: {}", e.getMessage(), e));
    }

    /**
     * Extrai texto de todas as páginas do PDF concatenadas.
     */
    private Mono<String> extractAllPagesText(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            StringBuilder sb = new StringBuilder();
            try (PdfReader reader = new PdfReader(inputStream);
                    PdfDocument pdfDoc = new PdfDocument(reader)) {
                int totalPages = pdfDoc.getNumberOfPages();
                for (int i = 1; i <= totalPages; i++) {
                    LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                    String pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i), strategy);
                    sb.append("=== PAGINA ").append(i).append(" ===\n");
                    sb.append(pageText).append("\n");
                }
            }
            return sb.toString();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> extractRawText(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            StringBuilder fullText = new StringBuilder();

            try (PdfReader reader = new PdfReader(inputStream);
                    PdfDocument pdfDoc = new PdfDocument(reader)) {

                int totalPages = pdfDoc.getNumberOfPages();
                log.info("📄 PDF tem {} páginas", totalPages);

                for (int i = 1; i <= totalPages; i++) {
                    LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                    String pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i), strategy);
                    fullText.append("=== PÁGINA ").append(i).append(" ===\n");
                    fullText.append(pageText).append("\n\n");
                }
            }

            return fullText.toString();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> extractRawTextFromPage(InputStream inputStream, int pageNumber) {
        return Mono.fromCallable(() -> {
            try (PdfReader reader = new PdfReader(inputStream);
                    PdfDocument pdfDoc = new PdfDocument(reader)) {

                if (pageNumber < 1 || pageNumber > pdfDoc.getNumberOfPages()) {
                    throw new IllegalArgumentException(
                            "Página " + pageNumber + " inválida. PDF tem " + pdfDoc.getNumberOfPages() + " páginas.");
                }

                LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                return PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageNumber), strategy);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Integer> findResumoPage(InputStream inputStream) {
        return Mono.fromCallable(() -> {
            try (PdfReader reader = new PdfReader(inputStream);
                    PdfDocument pdfDoc = new PdfDocument(reader)) {

                int totalPages = pdfDoc.getNumberOfPages();

                for (int i = 1; i <= totalPages; i++) {
                    LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                    String pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i), strategy);

                    if (pageText != null && pageText.toUpperCase().contains("RESUMO")) {
                        log.debug("🔍 'RESUMO' encontrado na página {}", i);
                        return i;
                    }
                }

                throw new IllegalArgumentException("Página RESUMO não encontrada no PDF");
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Faz o parsing das informações de IR a partir do texto extraído.
     */
    private IncomeTaxDeclarationService.IncomeTaxInfo parseIncomeTaxInfo(String primeiraPageText,
            String resumoPageText, String allPagesText) {
        String combinedText = primeiraPageText + "\n" + resumoPageText;

        // Dados Básicos
        String nome = extractString(combinedText, NOME_PATTERN);
        String cpf = extractString(combinedText, CPF_PATTERN);
        String anoCalendario = extractString(resumoPageText, ANO_CALENDARIO_PATTERN);
        if (anoCalendario == null) {
            anoCalendario = extractString(primeiraPageText, ANO_CALENDARIO_PATTERN);
        }
        String exercicio = extractString(resumoPageText, EXERCICIO_PATTERN);
        if (exercicio == null) {
            exercicio = extractString(primeiraPageText, EXERCICIO_PATTERN);
        }

        log.info("📌 Dados Básicos - Nome: {}, CPF: {}, Exercício: {}, Ano-Calendário: {}",
                nome, cpf, exercicio, anoCalendario);

        // IMPOSTO DEVIDO
        BigDecimal baseCalculoImposto = extractValorMonetario(resumoPageText, BASE_CALCULO_IMPOSTO_PATTERN);
        BigDecimal impostoDevido = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_PATTERN);
        BigDecimal deducaoIncentivo = extractValorMonetario(resumoPageText, DEDUCAO_INCENTIVO_PATTERN);
        BigDecimal impostoDevidoI = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_I_PATTERN);
        BigDecimal contribuicaoPrevEmpregadorDomestico = extractValorMonetario(resumoPageText,
                CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO_PATTERN);
        BigDecimal impostoDevidoII = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_II_PATTERN);
        BigDecimal impostoDevidoRRA = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_RRA_PATTERN);
        BigDecimal totalImpostoDevido = extractValorMonetario(resumoPageText, TOTAL_IMPOSTO_DEVIDO_PATTERN);
        BigDecimal saldoImpostoPagar = extractValorMonetario(resumoPageText, SALDO_IMPOSTO_PAGAR_PATTERN);

        impostoDevidoII = sanitizarImpostoDevidoII(
                impostoDevidoII, impostoDevidoI, contribuicaoPrevEmpregadorDomestico);

        // Fallback: usar Total se Imposto Devido simples não foi encontrado
        if ((impostoDevido == null || impostoDevido.compareTo(BigDecimal.ZERO) == 0) && totalImpostoDevido != null) {
            log.info("⚠️ Usando 'Total do imposto devido' como fallback para 'Imposto devido': {}", totalImpostoDevido);
            impostoDevido = totalImpostoDevido;
        }

        log.info("💰 IMPOSTO DEVIDO - Base: {}, Devido: {}, Saldo a Pagar: {}",
                baseCalculoImposto, impostoDevido, saldoImpostoPagar);

        // Rendimentos e Deduções Gerais
        // Tentar primeiro o padrão específico para TOTAL na seção RENDIMENTOS
        // TRIBUTÁVEIS
        BigDecimal rendimentosTributaveis = extractValorMonetario(resumoPageText,
                RENDIMENTOS_TRIBUTAVEIS_TOTAL_PATTERN);
        if (rendimentosTributaveis == null) {
            // Fallback: padrão mais simples
            rendimentosTributaveis = extractValorMonetario(resumoPageText, RENDIMENTOS_TRIBUTAVEIS_PATTERN);
        }

        BigDecimal deducoes = extractValorMonetario(resumoPageText, DEDUCOES_TOTAL_PATTERN);
        BigDecimal impostoRetidoFonteTitular = extractValorMonetario(resumoPageText,
                IMPOSTO_RETIDO_FONTE_TITULAR_PATTERN);
        BigDecimal impostoPagoTotal = extractValorMonetario(resumoPageText, IMPOSTO_PAGO_TOTAL_PATTERN);
        BigDecimal impostoRestituir = extractValorMonetario(resumoPageText, IMPOSTO_RESTITUIR_PATTERN);

        log.info("📊 Rendimentos/Deduções - Rendimentos: {}, Deduções: {}, Restituir: {}",
                rendimentosTributaveis, deducoes, impostoRestituir);

        // DEDUÇÕES Individuais
        BigDecimal deducoesContribPrevOficial = extractValorMonetario(resumoPageText,
                DEDUCOES_CONTRIB_PREV_OFICIAL_PATTERN);
        BigDecimal deducoesContribPrevRRA = extractValorMonetario(resumoPageText, DEDUCOES_CONTRIB_PREV_RRA_PATTERN);
        BigDecimal deducoesContribPrevCompl = extractValorMonetario(resumoPageText,
                DEDUCOES_CONTRIB_PREV_COMPL_ACIMA_LIMITE_PATTERN);
        if (deducoesContribPrevCompl == null) {
            deducoesContribPrevCompl = extractValorMonetario(resumoPageText,
                    DEDUCOES_CONTRIB_PREV_COMPL_PATTERN);
        }
        BigDecimal deducoesDependentes = extractValorMonetario(resumoPageText, DEDUCOES_DEPENDENTES_PATTERN);
        BigDecimal deducoesInstrucao = extractValorMonetario(resumoPageText, DEDUCOES_INSTRUCAO_PATTERN);
        BigDecimal deducoesMedicas = extractValorMonetario(resumoPageText, DEDUCOES_MEDICAS_PATTERN);
        BigDecimal deducoesPensaoJudicial = extractValorMonetario(resumoPageText, DEDUCOES_PENSAO_JUDICIAL_PATTERN);
        BigDecimal deducoesPensaoEscritura = extractValorMonetario(resumoPageText, DEDUCOES_PENSAO_ESCRITURA_PATTERN);
        BigDecimal deducoesPensaoRRA = extractValorMonetario(resumoPageText, DEDUCOES_PENSAO_RRA_PATTERN);
        BigDecimal deducoesLivroCaixa = extractValorMonetario(resumoPageText, DEDUCOES_LIVRO_CAIXA_PATTERN);

        log.info("📋 DEDUÇÕES - PrevOficial: {}, Médicas: {}, Instrução: {}",
                deducoesContribPrevOficial, deducoesMedicas, deducoesInstrucao);

        // IMPOSTO PAGO Individuais
        BigDecimal impostoRetidoFonteDependentes = extractValorMonetario(resumoPageText,
                IMPOSTO_RETIDO_FONTE_DEPENDENTES_PATTERN);
        BigDecimal carneLeaoTitular = extractValorMonetario(resumoPageText, CARNE_LEAO_TITULAR_PATTERN);
        BigDecimal carneLeaoDependentes = extractValorMonetario(resumoPageText, CARNE_LEAO_DEPENDENTES_PATTERN);
        BigDecimal impostoComplementar = extractValorMonetario(resumoPageText, IMPOSTO_COMPLEMENTAR_PATTERN);
        BigDecimal impostoPagoExterior = extractValorMonetario(resumoPageText, IMPOSTO_PAGO_EXTERIOR_PATTERN);
        BigDecimal impostoRetidoFonteLei11033 = extractValorMonetario(resumoPageText,
                IMPOSTO_RETIDO_FONTE_LEI_11033_PATTERN);
        BigDecimal impostoRetidoRRA = extractValorMonetario(resumoPageText, IMPOSTO_RETIDO_RRA_PATTERN);

        // Campos 2017+ — desconto simplificado é corrigido após rendimentos (ver abaixo)
        BigDecimal descontoSimplificado = null;
        BigDecimal aliquotaEfetiva = extractValorMonetario(resumoPageText, ALIQUOTA_EFETIVA_PATTERN);

        log.info("🔢 Campos 2017+ - Alíquota Efetiva: {}", aliquotaEfetiva);

        // ==========================================
        // NOVOS CAMPOS
        // ==========================================

        // Tipo de tributação (SIMPLIFICADO ou COMPLETO)
        String tipoTributacao;
        if (TIPO_TRIBUTACAO_SIMPLIFICADO_PATTERN.matcher(resumoPageText).find()) {
            tipoTributacao = "SIMPLIFICADO";
        } else if (TIPO_TRIBUTACAO_COMPLETO_PATTERN.matcher(resumoPageText).find()) {
            tipoTributacao = "COMPLETO";
        } else {
            // Fallback: presença do bloco simplificado no RESUMO
            tipoTributacao = resumoPageText.toUpperCase().contains("DESCONTO SIMPLIFICADO")
                    ? "SIMPLIFICADO" : "COMPLETO";
        }
        log.info("📋 Tipo de tributação: {}", tipoTributacao);

        // Identificação adicional
        String dataNascimento = extractString(primeiraPageText, DATA_NASCIMENTO_PATTERN);
        String tituloEleitoral = extractString(primeiraPageText, TITULO_ELEITORAL_PATTERN);
        String tipoDeclaracao = extractString(primeiraPageText, TIPO_DECLARACAO_PATTERN);
        if (tipoDeclaracao != null) tipoDeclaracao = tipoDeclaracao.trim();
        String dataEntrega = extractString(allPagesText, DATA_ENTREGA_PATTERN);
        log.info("📋 Identificação - Nascimento: {}, Título: {}, Tipo Declaração: {}, Entrega: {}",
                dataNascimento, tituloEleitoral, tipoDeclaracao, dataEntrega);

        // Evolução patrimonial — dois pares de valores (anterior e atual)
        BigDecimal bensAnterior = null;
        BigDecimal bensAtual = null;
        BigDecimal dividasAnterior = null;
        BigDecimal dividasAtual = null;

        // Tentativa 1: regex com label + valor inline / próxima linha
        Matcher bensMatcher = BENS_DIREITOS_PATTERN.matcher(resumoPageText);
        if (bensMatcher.find()) {
            bensAnterior = parseMonetaryString(bensMatcher.group(1));
            if (bensMatcher.find()) {
                bensAtual = parseMonetaryString(bensMatcher.group(1));
            }
        }
        Matcher dividasMatcher = DIVIDAS_ONUS_PATTERN.matcher(resumoPageText);
        if (dividasMatcher.find()) {
            dividasAnterior = parseMonetaryString(dividasMatcher.group(1));
            if (dividasMatcher.find()) {
                dividasAtual = parseMonetaryString(dividasMatcher.group(1));
            }
        }
        // Tentativa 2: extração posicional — procura secção EVOLUÇÃO PATRIMONIAL e coleta 4 valores
        if (bensAnterior == null) {
            List<BigDecimal> patrimonioVals = extractPatrimonioPositional(resumoPageText);
            if (patrimonioVals.size() >= 1) bensAnterior    = patrimonioVals.get(0);
            if (patrimonioVals.size() >= 2) bensAtual       = patrimonioVals.get(1);
            if (patrimonioVals.size() >= 3) dividasAnterior = patrimonioVals.get(2);
            if (patrimonioVals.size() >= 4) dividasAtual    = patrimonioVals.get(3);
        }
        log.info("🏠 Evolução Patrimonial - Bens ant: {}, Bens atu: {}, Dívidas ant: {}, Dívidas atu: {}",
                bensAnterior, bensAtual, dividasAnterior, dividasAtual);

        // Outras informações do RESUMO
        BigDecimal rendimentosIsentos = extractValorMonetario(resumoPageText, RENDIMENTOS_ISENTOS_PATTERN);
        BigDecimal rendimentosTributacaoExclusiva = extractValorMonetario(resumoPageText, RENDIMENTOS_TRIB_EXCLUSIVA_PATTERN);
        log.info("📊 Outras informações - Rendimentos isentos: {}, Tributação exclusiva: {}",
                rendimentosIsentos, rendimentosTributacaoExclusiva);

        // ==========================================
        // LINHAS INDIVIDUAIS DE RENDIMENTOS TRIBUTÁVEIS
        // ==========================================
        BigDecimal rendimentosTributaveisTitularPJ = extractValorMonetario(resumoPageText, REND_PJ_TITULAR_PATTERN);
        BigDecimal rendimentosTributaveisDependentesPJ = extractValorMonetario(resumoPageText, REND_PJ_DEPENDENTES_PATTERN);
        BigDecimal rendimentosTributaveisTitularPF = extractValorMonetario(resumoPageText, REND_PF_TITULAR_PATTERN);
        BigDecimal rendimentosTributaveisDependentesPF = extractValorMonetario(resumoPageText, REND_PF_DEPENDENTES_PATTERN);
        BigDecimal resultadoAtividadeRural = extractValorMonetario(resumoPageText, RESULTADO_ATIVIDADE_RURAL_PATTERN);
        BigDecimal rendimentosAcumuladosTitular = extractValorMonetario(resumoPageText, REND_ACUMULADOS_TITULAR_PATTERN);
        BigDecimal rendimentosAcumuladosDependentes = extractValorMonetario(resumoPageText, REND_ACUMULADOS_DEPENDENTES_PATTERN);
        log.info("📊 Rendimentos individuais (regex) - PJ Titular: {}, PJ Dep: {}, PF Titular: {}, PF Dep: {}, Rural: {}, Acum.Tit: {}, Acum.Dep: {}",
                rendimentosTributaveisTitularPJ, rendimentosTributaveisDependentesPJ,
                rendimentosTributaveisTitularPF, rendimentosTributaveisDependentesPF,
                resultadoAtividadeRural, rendimentosAcumuladosTitular, rendimentosAcumuladosDependentes);

        // ==========================================
        // CORREÇÃO POSICIONAL — RENDIMENTOS TRIBUTÁVEIS (RESUMO)
        //
        // Em layouts como exercício 2018 / AC 2017, o iText emite TODOS os
        // rótulos agrupados e só depois TODOS os valores em sequência. Os regex
        // "preguiçosos" por rótulo acabam casando o mesmo primeiro valor para
        // todas as sub-linhas (e o TOTAL também é capturado errado). Aqui
        // remapeamos os valores por POSIÇÃO e só sobrescrevemos quando a soma das
        // 7 linhas confere com o TOTAL, garantindo robustez para ambos layouts.
        // ==========================================
        RendimentosTributaveisBreakdown rtBreakdown = extractRendimentosTributaveisBreakdown(resumoPageText);
        if (rtBreakdown != null) {
            rendimentosTributaveis = rtBreakdown.total;
            rendimentosTributaveisTitularPJ = rtBreakdown.titularPJ;
            rendimentosTributaveisDependentesPJ = rtBreakdown.dependentesPJ;
            rendimentosTributaveisTitularPF = rtBreakdown.titularPF;
            rendimentosTributaveisDependentesPF = rtBreakdown.dependentesPF;
            rendimentosAcumuladosTitular = rtBreakdown.acumuladosTitular;
            rendimentosAcumuladosDependentes = rtBreakdown.acumuladosDependentes;
            resultadoAtividadeRural = rtBreakdown.atividadeRural;
            log.info("✅ Rendimentos tributáveis remapeados por POSIÇÃO - Total: {}, PJ Tit: {}, PJ Dep: {}, PF Tit: {}, PF Dep: {}, Acum.Tit: {}, Acum.Dep: {}, Rural: {}",
                    rendimentosTributaveis, rendimentosTributaveisTitularPJ, rendimentosTributaveisDependentesPJ,
                    rendimentosTributaveisTitularPF, rendimentosTributaveisDependentesPF,
                    rendimentosAcumuladosTitular, rendimentosAcumuladosDependentes, resultadoAtividadeRural);
        }

        // Desconto simplificado — só declaração Simplificada (regex simples captura o cabeçalho)
        if ("SIMPLIFICADO".equals(tipoTributacao)) {
            descontoSimplificado = extractDescontoSimplificadoResumo(
                    resumoPageText, rendimentosTributaveis, baseCalculoImposto);
        }
        log.info("🔢 Desconto Simplificado (corrigido): {}", descontoSimplificado);

        // Total deduções — regex pode capturar cabeçalho "DEDUÇÕES LEGAIS" + total de rendimentos
        deducoes = corrigirDeducoesTotalResumo(
                resumoPageText, deducoes, rendimentosTributaveis, baseCalculoImposto,
                deducoesContribPrevOficial, deducoesContribPrevRRA, deducoesContribPrevCompl,
                deducoesDependentes, deducoesInstrucao, deducoesMedicas,
                deducoesPensaoJudicial, deducoesPensaoEscritura, deducoesPensaoRRA, deducoesLivroCaixa);
        log.info("🔢 Total deduções (corrigido): {}", deducoes);

        CamposImpostoDevidoCorrigidos impostoDevidoCorrigido = corrigirCamposImpostoDevidoResumo(
                resumoPageText, tipoTributacao, baseCalculoImposto, impostoDevido, deducaoIncentivo,
                impostoDevidoI, impostoDevidoRRA, contribuicaoPrevEmpregadorDomestico, impostoDevidoII,
                saldoImpostoPagar, totalImpostoDevido, rendimentosTributaveis, deducoes, descontoSimplificado);
        baseCalculoImposto = impostoDevidoCorrigido.baseCalculoImposto();
        deducaoIncentivo = impostoDevidoCorrigido.deducaoIncentivo();
        impostoDevidoI = impostoDevidoCorrigido.impostoDevidoI();
        saldoImpostoPagar = impostoDevidoCorrigido.saldoImpostoPagar();
        log.info("💰 IMPOSTO DEVIDO (corrigido) - Base: {}, Dedução incentivo: {}, Devido I: {}, Saldo: {}",
                baseCalculoImposto, deducaoIncentivo, impostoDevidoI, saldoImpostoPagar);

        // ==========================================
        // OUTRAS INFORMAÇÕES
        // ==========================================
        BigDecimal impostoPagoGanhosCapital = extractValorMonetario(resumoPageText, IMPOSTO_PAGO_GANHOS_CAPITAL_PATTERN);
        BigDecimal impostoDevidoGanhosCapital = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_GANHOS_CAPITAL_PATTERN);
        BigDecimal impostoDevidoGanhosCapitalMoedaEstrangeira = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_GANHOS_CAPITAL_MOEDA_PATTERN);
        BigDecimal impostoPagoGanhosCapitalMoedaEstrangeira = extractValorMonetario(resumoPageText, IMPOSTO_PAGO_GANHOS_CAPITAL_MOEDA_PATTERN);
        BigDecimal impostoPagoRendaVariavel = extractValorMonetario(resumoPageText, IMPOSTO_PAGO_RENDA_VARIAVEL_PATTERN);
        BigDecimal impostoDevidoGanhosLiquidosRendaVariavel = extractValorMonetario(resumoPageText, IMPOSTO_DEVIDO_GANHOS_LIQUIDOS_RV_PATTERN);
        BigDecimal impostoAPagarGanhosCapitalMoedaEstrangeira = extractValorMonetario(resumoPageText, IMPOSTO_PAGAR_GANHO_CAPITAL_MOEDA_ESPECIE_PATTERN);
        BigDecimal rendimentosTributaveisExigSuspensa = extractValorMonetario(resumoPageText, REND_TRIBUTAVEIS_EXIG_SUSPENSA_PATTERN);
        BigDecimal depositosJudiciais = extractValorMonetario(resumoPageText, DEPOSITOS_JUDICIAIS_PATTERN);
        BigDecimal impostoDiferidoGanhosCapital = extractValorMonetario(resumoPageText, IMPOSTO_DIFERIDO_GANHOS_CAPITAL_PATTERN);
        BigDecimal doacoesPartidosPoliticos = extractValorMonetario(resumoPageText, DOACOES_PARTIDOS_POLITICOS_PATTERN);
        log.info("📋 Outras informações - PagoGC: {}, DevidoGC: {}, PagoRV: {}, ExigSuspensa: {}, DepJudiciais: {}, DifGC: {}",
                impostoPagoGanhosCapital, impostoDevidoGanhosCapital, impostoPagoRendaVariavel,
                rendimentosTributaveisExigSuspensa, depositosJudiciais, impostoDiferidoGanhosCapital);

        // Pagamentos efetuados — extrair da seção PAGAMENTOS EFETUADOS em todas as páginas
        List<PagamentoEfetuado> pagamentosEfetuados = extractPagamentosEfetuados(allPagesText);
        log.info("💳 Pagamentos efetuados: {} encontrados", pagamentosEfetuados.size());

        // Doações efetuadas — extrair da seção DOAÇÕES EFETUADAS em todas as páginas
        List<DoacaoEfetuada> doacoesEfetuadas = extractDoacoesEfetuadas(allPagesText);
        log.info("🎁 Doações efetuadas: {} encontradas", doacoesEfetuadas.size());

        // Fontes pagadoras — extrair de todas as páginas (seção PJ pode continuar após a 1ª)
        List<FontePagadora> fontesPagadoras = extractFontesPagadoras(allPagesText);
        log.info("🏢 Fontes pagadoras: {} encontradas", fontesPagadoras.size());

        // Controle da declaração
        String controle = extractString(allPagesText, CONTROLE_PATTERN);
        log.info("🔢 Controle: {}", controle);

        // Dependentes
        List<DependenteInfo> dependentes = extractDependentes(primeiraPageText);
        BigDecimal totalDeducaoDependentes = extractTotalDeducaoDependentes(primeiraPageText);
        log.info("👨‍👩‍👧 Dependentes: {} encontrados, total dedução: {}", dependentes.size(), totalDeducaoDependentes);

        // Alimentandos
        List<DependenteInfo> alimentandos = extractAlimentandos(primeiraPageText);
        log.info("🍽️ Alimentandos: {} encontrados", alimentandos.size());

        return new IncomeTaxDeclarationService.IncomeTaxInfo(
                nome, cpf, anoCalendario, exercicio,
                baseCalculoImposto, impostoDevido, deducaoIncentivo, impostoDevidoI,
                contribuicaoPrevEmpregadorDomestico, impostoDevidoII, impostoDevidoRRA,
                totalImpostoDevido, saldoImpostoPagar,
                rendimentosTributaveis, deducoes, impostoRetidoFonteTitular, impostoPagoTotal, impostoRestituir,
                deducoesContribPrevOficial, deducoesContribPrevRRA, deducoesContribPrevCompl,
                deducoesDependentes, deducoesInstrucao, deducoesMedicas,
                deducoesPensaoJudicial, deducoesPensaoEscritura, deducoesPensaoRRA, deducoesLivroCaixa,
                impostoRetidoFonteDependentes, carneLeaoTitular, carneLeaoDependentes,
                impostoComplementar, impostoPagoExterior, impostoRetidoFonteLei11033, impostoRetidoRRA,
                descontoSimplificado, aliquotaEfetiva,
                tipoTributacao, dataNascimento, tituloEleitoral, tipoDeclaracao, dataEntrega,
                bensAnterior, bensAtual, dividasAnterior, dividasAtual,
                rendimentosIsentos, rendimentosTributacaoExclusiva,
                pagamentosEfetuados,
                fontesPagadoras,
                controle,
                dependentes,
                totalDeducaoDependentes,
                alimentandos,
                // Linhas individuais de rendimentos tributáveis
                rendimentosTributaveisTitularPJ, rendimentosTributaveisDependentesPJ,
                rendimentosTributaveisTitularPF, rendimentosTributaveisDependentesPF,
                resultadoAtividadeRural, rendimentosAcumuladosTitular, rendimentosAcumuladosDependentes,
                // Outras informações
                impostoPagoGanhosCapital, impostoDevidoGanhosCapital,
                impostoDevidoGanhosCapitalMoedaEstrangeira, impostoPagoGanhosCapitalMoedaEstrangeira,
                impostoPagoRendaVariavel, impostoDevidoGanhosLiquidosRendaVariavel,
                impostoAPagarGanhosCapitalMoedaEstrangeira,
                rendimentosTributaveisExigSuspensa, depositosJudiciais,
                impostoDiferidoGanhosCapital, doacoesPartidosPoliticos,
                // Doações efetuadas
                doacoesEfetuadas);
    }

    /**
     * Converte string monetária brasileira para BigDecimal.
     */
    private BigDecimal parseMonetaryString(String valorStr) {
        if (valorStr == null) return null;
        try {
            return new BigDecimal(valorStr.replace(".", "").replace(",", "."));
        } catch (NumberFormatException e) {
            log.warn("⚠️ Erro ao converter valor monetário: {}", valorStr);
            return null;
        }
    }

    /**
     * Extrai pagamentos efetuados do texto completo do PDF.
     * Layout da seção PAGAMENTOS EFETUADOS (cada campo em linha separada):
     *   código
     *   nome do beneficiário
     *   CPF/CNPJ do beneficiário
     *   valor pago
     */
    private List<PagamentoEfetuado> extractPagamentosEfetuados(String allPagesText) {
        List<PagamentoEfetuado> pagamentos = new ArrayList<>();
        if (allPagesText == null) return pagamentos;

        // Encontrar a seção de PAGAMENTOS EFETUADOS
        int idx = allPagesText.toUpperCase().indexOf("PAGAMENTOS EFETUADOS");
        if (idx < 0) return pagamentos;

        // Pegar uma janela de texto após o cabeçalho (até DOAÇÕES ou DECLARAÇÃO DE BENS)
        String section = allPagesText.substring(idx);
        int endIdx = -1;
        String upper = section.toUpperCase();
        int doacoes = upper.indexOf("DOAÇÕES EFETUADAS");
        if (doacoes < 0) doacoes = upper.indexOf("DOACOES EFETUADAS");
        int bens = upper.indexOf("DECLARAÇÃO DE BENS");
        if (bens < 0) bens = upper.indexOf("DECLARACAO DE BENS");
        if (doacoes > 0 && (bens < 0 || doacoes < bens)) endIdx = doacoes;
        else if (bens > 0) endIdx = bens;
        if (endIdx > 0) section = section.substring(0, endIdx);

        log.debug("📋 Seção PAGAMENTOS EFETUADOS ({} chars): {}", section.length(),
                section.substring(0, Math.min(400, section.length())).replace("\n", "\\n"));

        // Tentar padrão estruturado multilinha
        Matcher m = PAGAMENTO_ENTRY_PATTERN.matcher(section);
        while (m.find()) {
            pagamentos.add(buildPagamentoFromMatch(m));
        }

        // Fallback: linha inline (layout SERPRO comum em PDFs 2016+)
        if (pagamentos.isEmpty()) {
            Matcher inline = PAGAMENTO_INLINE_PATTERN.matcher(section);
            while (inline.find()) {
                pagamentos.add(buildPagamentoFromMatch(inline));
            }
        }

        // Fallback: extração posicional linha a linha
        if (pagamentos.isEmpty()) {
            pagamentos = extractPagamentosPositional(section);
        }

        return pagamentos;
    }

    private PagamentoEfetuado buildPagamentoFromMatch(Matcher m) {
        String codigo = m.group(1).trim();
        String nome = m.group(2).trim();
        String cnpj = m.group(3).trim();
        String nit = m.group(4) != null ? m.group(4).trim() : null;
        BigDecimal valor = parseMonetaryString(m.group(5));
        BigDecimal parcNaoDedutivel = m.group(6) != null ? parseMonetaryString(m.group(6)) : null;
        log.info("💳 Pagamento encontrado: cód={}, nome={}, cnpj={}, valor={}, parc={}, nit={}",
                codigo, nome, cnpj, valor, parcNaoDedutivel, nit);
        return new PagamentoEfetuado(codigo, nome, cnpj, valor, parcNaoDedutivel, nit);
    }

    /**
     * Extração posicional de pagamentos quando o regex estruturado falha.
     * Estratégia: após o cabeçalho "CÓD. / NOME DO BENEFICIÁRIO / ...",
     * coletar linhas com: código, nome, CNPJ/CPF, valor pago, [parc. não dedutível], [NIT].
     */
    private List<PagamentoEfetuado> extractPagamentosPositional(String section) {
        List<PagamentoEfetuado> pagamentos = new ArrayList<>();
        String[] lines = section.split("\\r?\\n");

        Pattern codigoLine = Pattern.compile("^\\s*(\\d{1,3})\\s*$");
        Pattern cnpjLine = Pattern.compile("^\\s*([\\d]{2,3}\\.[\\d]{3}\\.[\\d]{3}/[\\d]{4}-[\\d]{2}|[\\d]{3}\\.[\\d]{3}\\.[\\d]{3}-[\\d]{2})\\s*$");
        Pattern valorLine = Pattern.compile("^\\s*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})\\s*$");
        Pattern nitFormattedLine = Pattern.compile("^\\s*([\\d]{3}\\.[\\d]{5}\\.[\\d]{2}-[\\d])\\s*$");
        Pattern nitPlainLine = Pattern.compile("^\\s*(\\d{11})\\s*$");

        int i = 0;
        while (i < lines.length) {
            Matcher codM = codigoLine.matcher(lines[i]);
            if (codM.matches()) {
                String codigo = codM.group(1);
                int j = i + 1;
                while (j < lines.length && lines[j].trim().isEmpty()) j++;
                if (j >= lines.length) { i++; continue; }
                String nome = lines[j].trim();
                int k = j + 1;
                while (k < lines.length && lines[k].trim().isEmpty()) k++;
                if (k >= lines.length) { i++; continue; }
                Matcher cnpjM = cnpjLine.matcher(lines[k]);
                if (!cnpjM.matches()) { i++; continue; }
                String cnpj = cnpjM.group(1);

                int l = k + 1;
                while (l < lines.length && lines[l].trim().isEmpty()) l++;
                if (l >= lines.length) { i++; continue; }

                String nit = null;
                String lineAfterCpf = lines[l].trim();
                Matcher nitFmtM = nitFormattedLine.matcher(lineAfterCpf);
                Matcher nitPlainM = nitPlainLine.matcher(lineAfterCpf);
                if (nitFmtM.matches()) {
                    nit = nitFmtM.group(1);
                    l++;
                    while (l < lines.length && lines[l].trim().isEmpty()) l++;
                } else if (nitPlainM.matches()) {
                    nit = nitPlainM.group(1);
                    l++;
                    while (l < lines.length && lines[l].trim().isEmpty()) l++;
                }

                if (l >= lines.length) { i++; continue; }
                Matcher valorM = valorLine.matcher(lines[l]);
                if (!valorM.matches()) { i++; continue; }
                BigDecimal valor = parseMonetaryString(valorM.group(1));

                BigDecimal parcNaoDedutivel = null;
                int next = l + 1;
                while (next < lines.length && lines[next].trim().isEmpty()) next++;
                if (next < lines.length) {
                    Matcher parcM = valorLine.matcher(lines[next]);
                    if (parcM.matches()) {
                        parcNaoDedutivel = parseMonetaryString(parcM.group(1));
                        l = next;
                    }
                }

                pagamentos.add(new PagamentoEfetuado(codigo, nome, cnpj, valor, parcNaoDedutivel, nit));
                log.info("💳 Pagamento posicional: cód={}, nome={}, cnpj={}, valor={}, parc={}, nit={}", codigo, nome, cnpj, valor, parcNaoDedutivel, nit);
                i = l + 1;
                continue;
            }
            i++;
        }
        return pagamentos;
    }

    /**
     * Extrai doações efetuadas do texto completo do PDF.
     * Estrutura similar à de pagamentos; "Sem Informações" retorna lista vazia.
     */
    private List<DoacaoEfetuada> extractDoacoesEfetuadas(String allPagesText) {
        List<DoacaoEfetuada> doacoes = new ArrayList<>();
        if (allPagesText == null) return doacoes;

        String upper = allPagesText.toUpperCase();
        int idx = upper.indexOf("DOA");
        // Encontrar especificamente "DOAÇÕES EFETUADAS"
        while (idx >= 0) {
            String snippet = upper.substring(idx, Math.min(idx + 30, upper.length()));
            if (snippet.contains("EFETUADAS") || snippet.contains("EFETUADA")) break;
            idx = upper.indexOf("DOA", idx + 1);
        }
        if (idx < 0) return doacoes;

        // Pegar janela da seção
        String section = allPagesText.substring(idx);
        String sectionUpper = section.toUpperCase();

        // Verificar "Sem Informações"
        if (sectionUpper.substring(0, Math.min(200, sectionUpper.length())).contains("SEM INFORMA")) {
            log.debug("🎁 DOAÇÕES EFETUADAS: Sem Informações");
            return doacoes;
        }

        // Delimitar seção até próximo cabeçalho
        int endIdx = -1;
        int bens = sectionUpper.indexOf("DECLARACAO DE BENS");
        if (bens < 0) bens = sectionUpper.indexOf("DECLARAÇÃO DE BENS");
        int pagamentos = sectionUpper.indexOf("PAGAMENTOS EFETUADOS");
        if (bens > 0) endIdx = bens;
        if (pagamentos > 0 && (endIdx < 0 || pagamentos < endIdx)) endIdx = pagamentos;
        if (endIdx > 0) section = section.substring(0, endIdx);

        log.debug("🎁 Seção DOAÇÕES EFETUADAS ({} chars): {}", section.length(),
                section.substring(0, Math.min(300, section.length())).replace("\n", "\\n"));

        // Padrão estruturado igual ao de pagamentos
        Pattern entryPattern = Pattern.compile(
                "(?m)^\\s*(\\d{1,3})\\s*\\n([^\\n]{3,80})\\n([\\d]{2,3}\\.[\\d]{3}\\.[\\d]{3}[/-][\\d]{4}-[\\d]{2}|[\\d]{3}\\.[\\d]{3}\\.[\\d]{3}-[\\d]{2})\\s*\\n([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
                Pattern.UNICODE_CASE | Pattern.MULTILINE);

        Matcher m = entryPattern.matcher(section);
        while (m.find()) {
            String codigo = m.group(1).trim();
            String nome = m.group(2).trim();
            String cnpj = m.group(3).trim();
            BigDecimal valor = parseMonetaryString(m.group(4));
            doacoes.add(new DoacaoEfetuada(codigo, nome, cnpj, valor));
            log.info("🎁 Doação encontrada: cód={}, nome={}, cnpj={}, valor={}", codigo, nome, cnpj, valor);
        }

        // Fallback posicional
        if (doacoes.isEmpty()) {
            String[] lines = section.split("\\r?\\n");
            Pattern codigoLine = Pattern.compile("^\\s*(\\d{1,3})\\s*$");
            Pattern cnpjLine = Pattern.compile("^\\s*([\\d]{2,3}\\.[\\d]{3}\\.[\\d]{3}/[\\d]{4}-[\\d]{2}|[\\d]{3}\\.[\\d]{3}\\.[\\d]{3}-[\\d]{2})\\s*$");
            Pattern valorLine = Pattern.compile("^\\s*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})\\s*$");
            int i = 0;
            while (i < lines.length) {
                Matcher codM = codigoLine.matcher(lines[i]);
                if (codM.matches()) {
                    String codigo = codM.group(1);
                    int j = i + 1; while (j < lines.length && lines[j].trim().isEmpty()) j++;
                    if (j >= lines.length) { i++; continue; }
                    String nome = lines[j].trim();
                    int k = j + 1; while (k < lines.length && lines[k].trim().isEmpty()) k++;
                    if (k >= lines.length) { i++; continue; }
                    Matcher cnpjM = cnpjLine.matcher(lines[k]);
                    if (!cnpjM.matches()) { i++; continue; }
                    String cnpj = cnpjM.group(1);
                    int l = k + 1; while (l < lines.length && lines[l].trim().isEmpty()) l++;
                    if (l >= lines.length) { i++; continue; }
                    Matcher valorM = valorLine.matcher(lines[l]);
                    if (!valorM.matches()) { i++; continue; }
                    BigDecimal valor = parseMonetaryString(valorM.group(1));
                    doacoes.add(new DoacaoEfetuada(codigo, nome, cnpj, valor));
                    log.info("🎁 Doação posicional: cód={}, nome={}, cnpj={}, valor={}", codigo, nome, cnpj, valor);
                    i = l + 1;
                    continue;
                }
                i++;
            }
        }

        return doacoes;
    }

    /**
     * Extrai todas as fontes pagadoras da seção "RENDIMENTOS TRIBUTÁVEIS RECEBIDOS DE
     * PESSOA JURÍDICA PELO TITULAR" da página 1.
     *
     * <p>Estratégia: ancora em cada ocorrência de "CNPJ/CPF: XXXXXXXXXX".
     * Para cada CNPJ, coleta o nome da empresa (linhas de texto não-numéricas
     * próximas) e os 5 valores monetários (rendimentos, contrib previdência,
     * imposto retido, 13º salário, IRRF 13º).</p>
     */
    private List<FontePagadora> extractFontesPagadoras(String pageText) {
        List<FontePagadora> result = new ArrayList<>();
        if (pageText == null) return result;

        // Delimitadores da seção
        Pattern secaoInicio = Pattern.compile(
                "RENDIMENTOS\\s+TRIBUT.VEIS\\s+RECEBIDOS\\s+DE\\s+PESSOA\\s+JUR.DICA\\s+PELO\\s+TITULAR",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Pattern secaoFim = Pattern.compile(
                "RENDIMENTOS\\s+TRIBUT.VEIS\\s+RECEBIDOS\\s+DE\\s+PESSOA\\s+JUR.DICA\\s+PELOS\\s+DEPENDENTES"
                + "|RENDIMENTOS\\s+TRIBUT.VEIS\\s+RECEBIDOS\\s+DE\\s+PESSOA\\s+F.SICA"
                + "|RENDIMENTOS\\s+ISENTOS",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        Matcher mInicio = secaoInicio.matcher(pageText);
        if (!mInicio.find()) {
            log.debug("🏢 Seção RENDIMENTOS PJ TITULAR não encontrada na página");
            return result;
        }
        int sectionStart = mInicio.end();

        Matcher mFim = secaoFim.matcher(pageText);
        mFim.region(sectionStart, pageText.length());
        int sectionEnd = mFim.find() ? mFim.start() : pageText.length();

        String section = pageText.substring(sectionStart, sectionEnd);
        log.debug("🏢 Seção RENDIMENTOS PJ ({} chars): {}",
                section.length(), section.substring(0, Math.min(300, section.length())).replace("\n", "\\n"));

        // Padrão de valor monetário
        Pattern valPattern = Pattern.compile("([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})");

        // Padrão CNPJ/CPF
        Pattern cnpjPattern = Pattern.compile(
                "CNPJ/CPF[:\\s]*([\\d]{2}\\.[\\d]{3}\\.[\\d]{3}/[\\d]{4}-[\\d]{2}|[\\d]{3}\\.[\\d]{3}\\.[\\d]{3}-[\\d]{2})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        // Coletar posições de todos os CNPJ na seção
        List<int[]> cnpjPos = new ArrayList<>(); // {matchStart, matchEnd, grpStart, grpEnd}
        Matcher mc = cnpjPattern.matcher(section);
        while (mc.find()) {
            cnpjPos.add(new int[]{mc.start(), mc.end(), mc.start(1), mc.end(1)});
        }

        if (cnpjPos.isEmpty()) {
            log.warn("🏢 Nenhum CNPJ/CPF encontrado na seção RENDIMENTOS PJ");
            return result;
        }

        // Palavras de cabeçalho a ignorar ao extrair o nome da empresa
        java.util.Set<String> SKIP = new java.util.HashSet<>(java.util.Arrays.asList(
                "NOME DA FONTE PAGADORA", "REND. RECEBIDOS", "DE PES. JURÍDICA", "DE PES. JURIDICA",
                "CONTR. PREVID.", "OFICIAL", "IMPOSTO RETIDO", "NA FONTE", "13º SALÁRIO", "13 SALÁRIO",
                "IRRF SOBRE 13º", "IRRF SOBRE 13", "SALÁRIO", "SALARIO",
                "(VALORES EM REAIS)", "VALORES EM REAIS", "TOTAL", "CNPJ/CPF"));

        // Para cada CNPJ, o bloco é: do final do CNPJ anterior até o final deste CNPJ
        int prevEnd = 0;
        for (int[] pos : cnpjPos) {
            String cnpj = section.substring(pos[2], pos[3]);

            // Bloco cobre o texto ANTES do CNPJ (desde o fim do anterior) + APÓS até próximo valor/linha
            // Para capturar valores que aparecem APÓS o CNPJ na mesma entrada, expandimos o bloco
            // até encontrar o início do próximo nome de empresa (linha de texto não-numérica após o CNPJ)
            String blockBefore = section.substring(prevEnd, pos[0]);
            prevEnd = pos[1]; // avança para depois do CNPJ

            // Coletar valores monetários do bloco antes do CNPJ
            List<BigDecimal> values = new ArrayList<>();
            Matcher valM = valPattern.matcher(blockBefore);
            while (valM.find()) {
                BigDecimal v = parseMonetaryString(valM.group(1));
                if (v != null) values.add(v);
            }

            // Se menos de 5 valores encontrados antes do CNPJ, buscar depois
            if (values.size() < 5) {
                // Pegar texto até o próximo CNPJ ou fim da seção
                int nextStart = (cnpjPos.indexOf(pos) + 1 < cnpjPos.size())
                        ? cnpjPos.get(cnpjPos.indexOf(pos) + 1)[0]
                        : section.length();
                String blockAfter = section.substring(pos[1], Math.min(nextStart, section.length()));
                valM = valPattern.matcher(blockAfter);
                while (valM.find() && values.size() < 5) {
                    BigDecimal v = parseMonetaryString(valM.group(1));
                    if (v != null) values.add(v);
                }
            }

            // Extrair nome da empresa do bloco anterior ao CNPJ
            String nome = extractNomeEmpresa(blockBefore, SKIP);

            // Mapear os 5 valores na ordem: rendimentos, contribPrev, impostoRetido, decimoTerceiro, irrfDecTerceiro
            BigDecimal rendimentos      = values.size() > 0 ? values.get(0) : null;
            BigDecimal contribPrev      = values.size() > 1 ? values.get(1) : null;
            BigDecimal impostoRetido    = values.size() > 2 ? values.get(2) : null;
            BigDecimal decimoTerceiro   = values.size() > 3 ? values.get(3) : null;
            BigDecimal irrfDecTerceiro  = values.size() > 4 ? values.get(4) : null;

            if (nome != null || cnpj != null) {
                FontePagadora fp = new FontePagadora(nome, cnpj, rendimentos, contribPrev,
                        impostoRetido, decimoTerceiro, irrfDecTerceiro);
                result.add(fp);
                log.info("🏢 Fonte pagadora: nome={}, cnpj={}, rend={}, prev={}, irrf={}, 13o={}, irrf13={}",
                        nome, cnpj, rendimentos, contribPrev, impostoRetido, decimoTerceiro, irrfDecTerceiro);
            }
        }

        return result;
    }

    /**
     * Extrai o nome da empresa de um bloco de texto, ignorando linhas de cabeçalho e números.
     */
    private String extractNomeEmpresa(String block, java.util.Set<String> skipKeywords) {
        if (block == null || block.isEmpty()) return null;
        String[] lines = block.split("[\\r\\n]+");
        StringBuilder name = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.matches("[\\d.,/\\-\\s]+")) continue; // linha só com números/pontuação
            if (trimmed.matches("(?i)\\(?valores.*")) continue;
            boolean skip = false;
            String upper = trimmed.toUpperCase();
            for (String kw : skipKeywords) {
                if (upper.contains(kw)) { skip = true; break; }
            }
            if (skip) continue;
            if (name.length() > 0) name.append(" ");
            name.append(trimmed);
        }
        String result = name.toString().trim();
        return result.isEmpty() ? null : result;
    }

    // ==========================================
    // DEPENDENTES E ALIMENTANDOS
    // ==========================================

    /**
     * Extrai a lista de dependentes da página 1 da declaração.
     *
     * <p>O PDF armazena os dados em colunas separadas (pdfminer/iText lê coluna a coluna):
     * primeiro os códigos, depois os nomes, depois as datas, depois os CPFs.
     * O método coleta cada grupo na ordem e combina por índice.</p>
     */
    private List<DependenteInfo> extractDependentes(String pageText) {
        return extractPessoas(pageText, "DEPENDENTES", "ALIMENTANDOS");
    }

    /**
     * Extrai a lista de alimentandos da página 1 da declaração.
     */
    private List<DependenteInfo> extractAlimentandos(String pageText) {
        return extractPessoas(pageText, "ALIMENTANDOS",
                "RENDIMENTOS TRIBUT");
    }

    /**
     * Extrai total de dedução com dependentes.
     */
    private BigDecimal extractTotalDeducaoDependentes(String pageText) {
        Pattern p = Pattern.compile(
                "TOTAL\\s+DE\\s+DEDU[ÇC][ÃA]O\\s+COM\\s+DEPENDENTES[\\s\\r\\n]*([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return extractValorMonetario(pageText, p);
    }

    /**
     * Extrai a lista de pessoas (dependentes ou alimentandos) de uma seção do PDF.
     *
     * <p>Estratégia: encontrar o bloco entre {@code sectionMarker} e {@code endMarker},
     * depois coletar grupos de: códigos (2 dígitos), nomes (letras maiúsculas), datas (DD/MM/YYYY)
     * e CPFs (XXX.XXX.XXX-XX) em ordem de aparição, combinando por índice.</p>
     */
    private List<DependenteInfo> extractPessoas(String pageText, String sectionMarker, String endMarker) {
        List<DependenteInfo> result = new ArrayList<>();
        if (pageText == null) return result;

        String upper = pageText.toUpperCase();
        int startIdx = upper.indexOf(sectionMarker);
        if (startIdx < 0) return result;
        startIdx += sectionMarker.length();

        int endIdx = upper.indexOf(endMarker, startIdx);
        if (endIdx < 0) endIdx = Math.min(startIdx + 2000, pageText.length()); // limitar janela

        String section = pageText.substring(startIdx, endIdx);

        // Verificar "Sem Informações"
        if (section.toUpperCase().contains("SEM INFORMA")) {
            log.debug("📋 Seção '{}' com Sem Informações", sectionMarker);
            return result;
        }

        // Coletar códigos (números de 2 dígitos que não fazem parte de datas/CPFs)
        // Os códigos típicos são: 21 (filho), 22 (filho universitário), 31 (pai/mãe), 41 (menor pobre), etc.
        List<String> codes = new ArrayList<>();
        Pattern codePattern = Pattern.compile("(?<![\\d./])\\b(\\d{2})\\b(?![\\d./])", Pattern.MULTILINE);
        Matcher codeMatcher = codePattern.matcher(section);
        while (codeMatcher.find()) {
            String code = codeMatcher.group(1);
            // Filtrar: deve estar numa linha que contém apenas o código
            int lineStart = section.lastIndexOf('\n', codeMatcher.start()) + 1;
            int lineEnd = section.indexOf('\n', codeMatcher.start());
            if (lineEnd < 0) lineEnd = section.length();
            String fullLine = section.substring(lineStart, lineEnd).trim();
            if (fullLine.matches("\\d{2}")) {
                codes.add(code);
            }
        }

        // Coletar datas de nascimento (DD/MM/YYYY)
        List<String> dates = new ArrayList<>();
        Pattern datePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
        Matcher dateMatcher = datePattern.matcher(section);
        while (dateMatcher.find()) {
            dates.add(dateMatcher.group(1));
        }

        // Coletar CPFs (XXX.XXX.XXX-XX)
        List<String> cpfs = new ArrayList<>();
        Pattern cpfPattern = Pattern.compile("(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})");
        Matcher cpfMatcher = cpfPattern.matcher(section);
        while (cpfMatcher.find()) {
            cpfs.add(cpfMatcher.group(1));
        }

        // Coletar nomes: linhas com apenas letras maiúsculas, acentos e espaços (>= 3 chars)
        // Excluir cabeçalhos conhecidos
        java.util.Set<String> SKIP_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
                "CÓDIGO", "CODIGO", "NOME", "DATA DE NASCIMENTO", "CPF",
                "TOTAL DE DEDUÇÃO COM DEPENDENTES", "TOTAL DE DEDUCAO COM DEPENDENTES",
                "ALIMENTANDOS", "DEPENDENTES", "SEM INFORMAÇÕES", "SEM INFORMACOES"));
        List<String> names = new ArrayList<>();
        Pattern namePattern = Pattern.compile(
                "^([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇA-Z][A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇa-záéíóúàèìòùâêîôûãõç\\s]{2,})$",
                Pattern.MULTILINE | Pattern.UNICODE_CASE);
        Matcher nameMatcher = namePattern.matcher(section);
        while (nameMatcher.find()) {
            String candidate = nameMatcher.group(1).trim();
            if (!SKIP_NAMES.contains(candidate.toUpperCase())
                    && !candidate.matches("[\\d\\s.,/-]+")
                    && candidate.length() >= 5) {
                names.add(candidate);
            }
        }

        // Combinar por índice
        int count = codes.size();
        if (count == 0 && !names.isEmpty()) count = names.size(); // fallback se sem códigos
        count = Math.min(count, Math.min(
                Math.max(names.size(), 1),
                Math.min(dates.size() > 0 ? dates.size() : Integer.MAX_VALUE,
                         cpfs.size() > 0 ? cpfs.size() : Integer.MAX_VALUE)));

        for (int i = 0; i < count; i++) {
            String code  = i < codes.size() ? codes.get(i) : null;
            String name  = i < names.size() ? names.get(i) : null;
            String date  = i < dates.size() ? dates.get(i) : null;
            String cpf   = i < cpfs.size()  ? cpfs.get(i)  : null;
            if (name != null || cpf != null) {
                result.add(new DependenteInfo(code, name, date, cpf));
                log.info("👤 Pessoa '{}' extraída: código={}, nome={}, nasc={}, cpf={}",
                        sectionMarker, code, name, date, cpf);
            }
        }

        return result;
    }

    /**
     * Extrai valores do bloco EVOLUÇÃO PATRIMONIAL da página RESUMO.
     * Retorna lista com: [bensAnterior, bensAtual, dividasAnterior, dividasAtual].
     * O bloco tem 4 labels e depois os 4 valores no PDF de duas colunas do IR.
     */
    private List<BigDecimal> extractPatrimonioPositional(String resumoPageText) {
        List<BigDecimal> vals = new ArrayList<>();
        if (resumoPageText == null) return vals;

        String upper = resumoPageText.toUpperCase();
        int idx = upper.indexOf("EVOLU");
        if (idx < 0) idx = upper.indexOf("BENS E DIREITOS");
        if (idx < 0) return vals;

        String section = resumoPageText.substring(idx);
        // Extrair todos os valores monetários grandes (> 1.000,00) deste trecho
        // Os valores de bens tendem a ser > 0, dívidas podem ser 0
        Pattern valPattern = Pattern.compile("([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})");
        Matcher m = valPattern.matcher(section);
        while (m.find() && vals.size() < 4) {
            BigDecimal v = parseMonetaryString(m.group(1));
            if (v != null) vals.add(v);
        }
        log.debug("🏠 Patrimônio posicional extraído: {}", vals);
        return vals;
    }

    /** Linhas do bloco "RENDIMENTOS TRIBUTÁVEIS" do RESUMO, na ordem do PDF. */
    private static class RendimentosTributaveisBreakdown {
        BigDecimal total;
        BigDecimal titularPJ;
        BigDecimal dependentesPJ;
        BigDecimal titularPF;
        BigDecimal dependentesPF;
        BigDecimal acumuladosTitular;
        BigDecimal acumuladosDependentes;
        BigDecimal atividadeRural;
    }

    /**
     * Extração POSICIONAL da seção "RENDIMENTOS TRIBUTÁVEIS" do RESUMO.
     *
     * <p>Em layouts como o exercício 2018 / AC 2017, o iText emite todos os
     * rótulos agrupados e só depois os valores em sequência:</p>
     *
     * <pre>
     * RENDIMENTOS TRIBUTÁVEIS  Recebidos de PJ pelo titular ... Atividade Rural  TOTAL
     * 373.152,82 10.560,00 0,00 0,00 0,00 0,00 0,00
     * 383.712,82
     * </pre>
     *
     * <p>Mapeia os valores por posição: os primeiros são as 7 sub-linhas (na
     * ordem do PDF) e o último é o TOTAL. Só retorna resultado quando a soma das
     * sub-linhas confere com o TOTAL — caso contrário devolve {@code null} para
     * preservar a extração por regex.</p>
     *
     * @return breakdown validado ou {@code null}.
     */
    private RendimentosTributaveisBreakdown extractRendimentosTributaveisBreakdown(String resumoPageText) {
        if (resumoPageText == null) return null;

        String upper = resumoPageText.toUpperCase();
        int start = upper.indexOf("RENDIMENTOS TRIBUT");
        if (start < 0) return null;
        // Fim da seção: início de DEDUÇÕES (próximo bloco do RESUMO).
        int end = upper.indexOf("DEDU", start + "RENDIMENTOS TRIBUT".length());
        String section = (end > start) ? resumoPageText.substring(start, end) : resumoPageText.substring(start);

        Pattern valPattern = Pattern.compile("([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})");
        Matcher m = valPattern.matcher(section);
        List<BigDecimal> vals = new ArrayList<>();
        while (m.find()) {
            BigDecimal v = parseMonetaryString(m.group(1));
            if (v != null) vals.add(v);
        }
        if (vals.size() < 2) return null;

        BigDecimal total = vals.get(vals.size() - 1);
        List<BigDecimal> subs = vals.subList(0, vals.size() - 1);

        BigDecimal soma = BigDecimal.ZERO;
        for (BigDecimal s : subs) {
            soma = soma.add(s);
        }
        if (soma.compareTo(total) != 0) {
            log.warn("⚠️ Breakdown posicional de rendimentos tributáveis descartado (soma {} != total {}); valores: {}",
                    soma, total, vals);
            return null;
        }

        RendimentosTributaveisBreakdown b = new RendimentosTributaveisBreakdown();
        b.total = total;
        b.titularPJ = subAt(subs, 0);
        b.dependentesPJ = subAt(subs, 1);
        b.titularPF = subAt(subs, 2);
        b.dependentesPF = subAt(subs, 3);
        b.acumuladosTitular = subAt(subs, 4);
        b.acumuladosDependentes = subAt(subs, 5);
        b.atividadeRural = subAt(subs, 6);
        return b;
    }

    /** Retorna o valor da posição ou ZERO (linhas omitidas no PDF contam como 0,00). */
    private BigDecimal subAt(List<BigDecimal> subs, int index) {
        return index < subs.size() ? subs.get(index) : BigDecimal.ZERO;
    }

    /**
     * Extrai o valor do campo "Desconto Simplificado" do RESUMO.
     *
     * <p>Regex simples por rótulo casa primeiro com o cabeçalho
     * "TRIBUTAÇÃO UTILIZANDO O DESCONTO SIMPLIFICADO" e associa erroneamente o total
     * de rendimentos. Esta rotina ignora títulos de seção, tenta posição no bloco
     * simplificado e, por fim, deriva {@code rendimentos − base}.</p>
     */
    private BigDecimal extractDescontoSimplificadoResumo(
            String resumoPageText,
            BigDecimal rendimentosTributaveis,
            BigDecimal baseCalculoImposto) {

        BigDecimal fromLabel = extractDescontoSimplificadoRotulo(resumoPageText);
        if (isDescontoSimplificadoPlausivel(fromLabel, rendimentosTributaveis)) {
            return fromLabel;
        }

        BigDecimal positional = extractDescontoSimplificadoPosicional(
                resumoPageText, rendimentosTributaveis, baseCalculoImposto);
        if (isDescontoSimplificadoPlausivel(positional, rendimentosTributaveis)) {
            log.info("✅ Desconto simplificado extraído por POSIÇÃO: {}", positional);
            return positional;
        }

        if (rendimentosTributaveis != null && baseCalculoImposto != null) {
            BigDecimal derived = rendimentosTributaveis.subtract(baseCalculoImposto);
            if (isDescontoSimplificadoPlausivel(derived, rendimentosTributaveis)) {
                log.info("✅ Desconto simplificado derivado (rendimentos − base): {}", derived);
                return derived;
            }
        }

        if (fromLabel != null && !isDescontoSimplificadoPlausivel(fromLabel, rendimentosTributaveis)) {
            log.warn("⚠️ Desconto simplificado descartado (valor {} inconsistente com rendimentos {})",
                    fromLabel, rendimentosTributaveis);
        }
        return null;
    }

    private boolean isDescontoSimplificadoPlausivel(BigDecimal desconto, BigDecimal rendimentos) {
        if (desconto == null || desconto.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (rendimentos == null) {
            return true;
        }
        return desconto.compareTo(rendimentos) < 0;
    }

    /** Campo "Desconto Simplificado" — ignora cabeçalho e título da seção. */
    private BigDecimal extractDescontoSimplificadoRotulo(String resumoPageText) {
        if (resumoPageText == null) {
            return null;
        }

        Pattern labelPattern = Pattern.compile("(?i)desconto\\s+simplificado");
        Pattern valPattern = Pattern.compile("([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})");
        Matcher labelMatcher = labelPattern.matcher(resumoPageText);

        BigDecimal best = null;
        while (labelMatcher.find()) {
            int pos = labelMatcher.start();
            String before = resumoPageText.substring(Math.max(0, pos - 40), pos).toUpperCase();
            if (before.contains("UTILIZANDO O ") || before.contains(" E DESCONTO")) {
                continue;
            }
            String after = resumoPageText.substring(
                    labelMatcher.end(), Math.min(resumoPageText.length(), labelMatcher.end() + 300));
            Matcher valueMatcher = valPattern.matcher(after);
            if (valueMatcher.find()) {
                best = parseMonetaryString(valueMatcher.group(1));
            }
        }
        return best;
    }

    /**
     * No bloco simplificado do RESUMO, os valores após o TOTAL de rendimentos seguem a ordem:
     * desconto simplificado, base, imposto devido, …
     */
    private BigDecimal extractDescontoSimplificadoPosicional(
            String resumoPageText,
            BigDecimal rendimentosTributaveis,
            BigDecimal baseCalculoImposto) {
        if (resumoPageText == null || rendimentosTributaveis == null) {
            return null;
        }

        String upper = resumoPageText.toUpperCase();
        int start = upper.indexOf("RENDIMENTOS TRIBUT");
        if (start < 0) {
            return null;
        }

        int impostoPago = upper.indexOf("IMPOSTO PAGO", start);
        int dedu = upper.indexOf("DEDU", start + 20);
        int end = resumoPageText.length();
        if (impostoPago > start) {
            end = Math.min(end, impostoPago);
        }
        if (dedu > start) {
            end = Math.min(end, dedu);
        }

        String section = resumoPageText.substring(start, end);
        Pattern valPattern = Pattern.compile("([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})");
        Matcher m = valPattern.matcher(section);
        List<BigDecimal> vals = new ArrayList<>();
        while (m.find()) {
            BigDecimal v = parseMonetaryString(m.group(1));
            if (v != null) {
                vals.add(v);
            }
        }

        if (baseCalculoImposto != null) {
            for (int i = 0; i < vals.size() - 1; i++) {
                BigDecimal candidato = vals.get(i);
                BigDecimal baseSeguinte = vals.get(i + 1);
                if (rendimentosTributaveis.subtract(candidato).compareTo(baseSeguinte) == 0
                        && baseSeguinte.compareTo(baseCalculoImposto) == 0) {
                    return candidato;
                }
            }
        }

        for (int j = vals.size() - 1; j >= 0; j--) {
            if (vals.get(j).compareTo(rendimentosTributaveis) == 0 && j + 1 < vals.size()) {
                return vals.get(j + 1);
            }
        }
        return null;
    }

    /**
     * Corrige o TOTAL de deduções quando o regex casa com o cabeçalho "DEDUÇÕES LEGAIS"
     * e associa erroneamente o total de rendimentos.
     */
    /**
     * Corrige campos do bloco IMPOSTO DEVIDO quando o iText emite rótulos e valores
     * desalinhados (valores antes do rótulo ou vazamento de coluna).
     */
    private record CamposImpostoDevidoCorrigidos(
            BigDecimal baseCalculoImposto,
            BigDecimal deducaoIncentivo,
            BigDecimal impostoDevidoI,
            BigDecimal saldoImpostoPagar) {
    }

    private CamposImpostoDevidoCorrigidos corrigirCamposImpostoDevidoResumo(
            String resumoPageText,
            String tipoTributacao,
            BigDecimal baseCalculoExtraido,
            BigDecimal impostoDevido,
            BigDecimal deducaoIncentivoExtraido,
            BigDecimal impostoDevidoIExtraido,
            BigDecimal impostoDevidoRRA,
            BigDecimal contribuicaoPrevEmpregadorDomestico,
            BigDecimal impostoDevidoII,
            BigDecimal saldoImpostoPagarExtraido,
            BigDecimal totalImpostoDevido,
            BigDecimal rendimentosTributaveis,
            BigDecimal deducoesTotal,
            BigDecimal descontoSimplificado) {

        BigDecimal base = baseCalculoExtraido;
        BigDecimal deducaoIncentivo = deducaoIncentivoExtraido;
        BigDecimal impostoDevidoI = impostoDevidoIExtraido;
        BigDecimal saldoPagar = saldoImpostoPagarExtraido;

        if ("COMPLETO".equals(tipoTributacao)
                && rendimentosTributaveis != null
                && deducoesTotal != null) {
            BigDecimal baseDerivada = rendimentosTributaveis.subtract(deducoesTotal)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            if (isBaseCalculoPlausivel(baseDerivada, rendimentosTributaveis)) {
                if (base == null || base.compareTo(baseDerivada) != 0) {
                    log.info("✅ Base de cálculo derivada (rendimentos − deduções): {}", baseDerivada);
                }
                base = baseDerivada;
            }
        } else if ("SIMPLIFICADO".equals(tipoTributacao)
                && rendimentosTributaveis != null
                && descontoSimplificado != null) {
            BigDecimal baseDerivada = rendimentosTributaveis.subtract(descontoSimplificado)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            if (isBaseCalculoPlausivel(baseDerivada, rendimentosTributaveis)) {
                if (base == null || base.compareTo(baseDerivada) != 0) {
                    log.info("✅ Base de cálculo derivada (rendimentos − desconto simplificado): {}", baseDerivada);
                }
                base = baseDerivada;
            }
        }

        if (base == null || !isBaseCalculoPlausivel(base, rendimentosTributaveis)) {
            BigDecimal baseAntesRotulo = extractValorMonetarioAntesRotulo(
                    resumoPageText, "base de cálculo do imposto");
            if (baseAntesRotulo != null && isBaseCalculoPlausivel(baseAntesRotulo, rendimentosTributaveis)) {
                log.info("✅ Base de cálculo extraída antes do rótulo: {}", baseAntesRotulo);
                base = baseAntesRotulo;
            }
        }

        BigDecimal deducaoAntesRotulo = extractValorMonetarioAntesRotulo(
                resumoPageText, "dedução de incentivo");
        if (deducaoAntesRotulo != null) {
            if (impostoDevido != null && deducaoAntesRotulo.compareTo(impostoDevido) == 0) {
                deducaoIncentivo = BigDecimal.ZERO;
            } else {
                deducaoIncentivo = deducaoAntesRotulo;
            }
        } else if (deducaoIncentivo != null
                && impostoDevido != null
                && deducaoIncentivo.compareTo(impostoDevido) == 0) {
            log.warn("⚠️ Dedução de incentivo corrigida (duplicata do Imposto devido): 0,00");
            deducaoIncentivo = BigDecimal.ZERO;
        }

        impostoDevidoI = sanitizarImpostoDevidoI(
                impostoDevidoI, impostoDevido, deducaoIncentivo, impostoDevidoRRA,
                contribuicaoPrevEmpregadorDomestico, impostoDevidoII, totalImpostoDevido);

        BigDecimal saldoInline = extractValorMonetario(resumoPageText, SALDO_IMPOSTO_PAGAR_PATTERN);
        if (saldoInline != null) {
            saldoPagar = saldoInline;
        }

        return new CamposImpostoDevidoCorrigidos(base, deducaoIncentivo, impostoDevidoI, saldoPagar);
    }

    private boolean isBaseCalculoPlausivel(BigDecimal base, BigDecimal rendimentos) {
        if (base == null || base.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        if (rendimentos == null) {
            return true;
        }
        return base.compareTo(rendimentos) < 0;
    }

    /**
     * No RESUMO IRPF, o iText frequentemente emite o valor monetário na linha
     * imediatamente anterior ao rótulo (layout de duas colunas).
     */
    private BigDecimal extractValorMonetarioAntesRotulo(String text, String rotulo) {
        if (text == null || rotulo == null || rotulo.isBlank()) {
            return null;
        }
        Pattern rotuloPattern = Pattern.compile("(?i)" + Pattern.quote(rotulo));
        Matcher matcher = rotuloPattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String before = text.substring(Math.max(0, matcher.start() - 120), matcher.start());
        Pattern valPattern = Pattern.compile("([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})");
        Matcher valueMatcher = valPattern.matcher(before);
        BigDecimal last = null;
        while (valueMatcher.find()) {
            last = parseMonetaryString(valueMatcher.group(1));
        }
        return last;
    }

    /**
     * Anula Imposto devido I quando o regex captura o mesmo valor de Imposto devido
     * por vazamento de coluna (sem INSS doméstico, RRA ou dedução de incentivo).
     */
    private BigDecimal sanitizarImpostoDevidoI(
            BigDecimal impostoDevidoI,
            BigDecimal impostoDevido,
            BigDecimal deducaoIncentivo,
            BigDecimal impostoDevidoRRA,
            BigDecimal contribuicaoPrevEmpregadorDomestico,
            BigDecimal impostoDevidoII,
            BigDecimal totalImpostoDevido) {
        if (impostoDevidoI == null || impostoDevido == null) {
            return impostoDevidoI;
        }
        if (impostoDevidoI.compareTo(impostoDevido) != 0) {
            return impostoDevidoI;
        }
        // Só zera quando o total do imposto devido coincide com o imposto devido bruto
        // (layout 2024+ em que a linha I fica 0,00 apesar do vazamento de coluna).
        if (totalImpostoDevido == null || totalImpostoDevido.compareTo(impostoDevido) != 0) {
            return impostoDevidoI;
        }
        if (nvlBigDecimal(deducaoIncentivo).compareTo(BigDecimal.ZERO) > 0) {
            return impostoDevidoI;
        }
        if (nvlBigDecimal(impostoDevidoRRA).compareTo(BigDecimal.ZERO) > 0) {
            return impostoDevidoI;
        }
        if (nvlBigDecimal(contribuicaoPrevEmpregadorDomestico).compareTo(BigDecimal.ZERO) > 0) {
            return impostoDevidoI;
        }
        if (impostoDevidoII != null && impostoDevidoII.compareTo(BigDecimal.ZERO) > 0) {
            return impostoDevidoI;
        }
        log.warn("⚠️ Imposto devido I descartado (duplicata de layout; total = imposto devido): {}", impostoDevidoI);
        return BigDecimal.ZERO;
    }

    private BigDecimal corrigirDeducoesTotalResumo(
            String resumoPageText,
            BigDecimal deducoesExtraido,
            BigDecimal rendimentosTributaveis,
            BigDecimal baseCalculoImposto,
            BigDecimal prevOficial,
            BigDecimal prevOficialRra,
            BigDecimal prevCompl,
            BigDecimal dependentes,
            BigDecimal instrucao,
            BigDecimal medicas,
            BigDecimal pensaoJudicial,
            BigDecimal pensaoEscritura,
            BigDecimal pensaoRra,
            BigDecimal livroCaixa) {

        BigDecimal somaLinhas = somarLinhasDeducoesResumo(
                prevOficial, prevOficialRra, prevCompl, dependentes, instrucao, medicas,
                pensaoJudicial, pensaoEscritura, pensaoRra, livroCaixa);

        if (somaLinhas.compareTo(BigDecimal.ZERO) > 0
                && isDeducoesTotalPlausivel(somaLinhas, rendimentosTributaveis)) {
            if (deducoesExtraido != null && deducoesExtraido.compareTo(somaLinhas) != 0) {
                log.warn("⚠️ Total deduções corrigido pela soma das linhas: {} -> {}", deducoesExtraido, somaLinhas);
            }
            return somaLinhas;
        }

        if (isDeducoesTotalPlausivel(deducoesExtraido, rendimentosTributaveis)) {
            return deducoesExtraido;
        }

        BigDecimal positional = extractDeducoesTotalPosicional(
                resumoPageText, rendimentosTributaveis, baseCalculoImposto);
        if (isDeducoesTotalPlausivel(positional, rendimentosTributaveis)) {
            log.info("✅ Total deduções extraído por POSIÇÃO: {}", positional);
            return positional;
        }

        if (rendimentosTributaveis != null && baseCalculoImposto != null) {
            BigDecimal derived = rendimentosTributaveis.subtract(baseCalculoImposto);
            if (isDeducoesTotalPlausivel(derived, rendimentosTributaveis)) {
                log.info("✅ Total deduções derivado (rendimentos − base): {}", derived);
                return derived;
            }
        }

        if (deducoesExtraido != null && !isDeducoesTotalPlausivel(deducoesExtraido, rendimentosTributaveis)) {
            log.warn("⚠️ Total deduções descartado (valor {} inconsistente com rendimentos {})",
                    deducoesExtraido, rendimentosTributaveis);
        }
        return isDeducoesTotalPlausivel(deducoesExtraido, rendimentosTributaveis) ? deducoesExtraido : null;
    }

    private BigDecimal somarLinhasDeducoesResumo(
            BigDecimal prevOficial,
            BigDecimal prevOficialRra,
            BigDecimal prevCompl,
            BigDecimal dependentes,
            BigDecimal instrucao,
            BigDecimal medicas,
            BigDecimal pensaoJudicial,
            BigDecimal pensaoEscritura,
            BigDecimal pensaoRra,
            BigDecimal livroCaixa) {
        return nvlBigDecimal(prevOficial)
                .add(nvlBigDecimal(prevOficialRra))
                .add(nvlBigDecimal(prevCompl))
                .add(nvlBigDecimal(dependentes))
                .add(nvlBigDecimal(instrucao))
                .add(nvlBigDecimal(medicas))
                .add(nvlBigDecimal(pensaoJudicial))
                .add(nvlBigDecimal(pensaoEscritura))
                .add(nvlBigDecimal(pensaoRra))
                .add(nvlBigDecimal(livroCaixa))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private boolean isDeducoesTotalPlausivel(BigDecimal deducoes, BigDecimal rendimentos) {
        if (deducoes == null || deducoes.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        if (rendimentos == null) {
            return true;
        }
        return deducoes.compareTo(rendimentos) < 0;
    }

    private BigDecimal extractDeducoesTotalPosicional(
            String resumoPageText,
            BigDecimal rendimentosTributaveis,
            BigDecimal baseCalculoImposto) {
        if (resumoPageText == null) {
            return null;
        }

        String upper = resumoPageText.toUpperCase();
        int start = upper.indexOf("DEDU");
        if (start < 0) {
            return null;
        }
        // Ignora cabeçalho "TRIBUTAÇÃO ... DEDUÇÕES LEGAIS"
        String beforeStart = upper.substring(Math.max(0, start - 30), start);
        if (beforeStart.contains("UTILIZANDO AS ") || beforeStart.contains("TRIBUT")) {
            int next = upper.indexOf("DEDU", start + 4);
            if (next > start) {
                start = next;
            }
        }

        int impostoDevido = upper.indexOf("IMPOSTO DEVIDO", start);
        int baseCalculo = upper.indexOf("BASE DE C", start);
        int end = resumoPageText.length();
        if (impostoDevido > start) {
            end = Math.min(end, impostoDevido);
        }
        if (baseCalculo > start) {
            end = Math.min(end, baseCalculo);
        }

        String section = resumoPageText.substring(start, end);
        Pattern valPattern = Pattern.compile("([\\d]{1,3}(?:[.]\\d{3})*,\\d{2})");
        Matcher m = valPattern.matcher(section);
        List<BigDecimal> vals = new ArrayList<>();
        while (m.find()) {
            BigDecimal v = parseMonetaryString(m.group(1));
            if (v != null) {
                vals.add(v);
            }
        }
        if (vals.isEmpty()) {
            return null;
        }

        if (baseCalculoImposto != null) {
            for (int i = vals.size() - 1; i >= 0; i--) {
                BigDecimal candidato = vals.get(i);
                if (rendimentosTributaveis != null
                        && rendimentosTributaveis.subtract(candidato).compareTo(baseCalculoImposto) == 0) {
                    return candidato;
                }
            }
        }

        return vals.get(vals.size() - 1);
    }

    private BigDecimal nvlBigDecimal(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Anula Imposto devido II quando o regex captura erroneamente o mesmo valor de Imposto devido I
     * em declarações sem crédito de INSS doméstico.
     */
    private BigDecimal sanitizarImpostoDevidoII(
            BigDecimal impostoDevidoII,
            BigDecimal impostoDevidoI,
            BigDecimal contribuicaoPrevEmpregadorDomestico) {
        if (impostoDevidoII == null) {
            return null;
        }
        BigDecimal inss = nvlBigDecimal(contribuicaoPrevEmpregadorDomestico);
        if (inss.compareTo(BigDecimal.ZERO) > 0) {
            return impostoDevidoII;
        }
        if (impostoDevidoI != null && impostoDevidoII.compareTo(impostoDevidoI) == 0) {
            log.warn("⚠️ Imposto devido II descartado (igual ao Imposto devido I sem INSS doméstico): {}", impostoDevidoII);
            return null;
        }
        return impostoDevidoII;
    }

    /**
     * Extrai uma string usando um padrão regex.
     */
    private String extractString(String text, Pattern pattern) {
        if (text == null)
            return null;
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            log.debug("✅ Extraído '{}' com padrão {}", result,
                    pattern.pattern().substring(0, Math.min(50, pattern.pattern().length())));
            return result;
        }
        return null;
    }

    /**
     * Extrai um valor monetário (BigDecimal) usando um padrão regex.
     * Converte formato brasileiro (1.234,56) para BigDecimal.
     */
    private BigDecimal extractValorMonetario(String text, Pattern pattern) {
        if (text == null)
            return null;

        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            // Procura o primeiro grupo que contém um valor
            String valorStr = null;
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null && matcher.group(i).matches("[\\d.,]+")) {
                    valorStr = matcher.group(i);
                    break;
                }
            }

            if (valorStr != null) {
                try {
                    // Converte formato brasileiro para padrão numérico
                    String valorNormalizado = valorStr.replace(".", "").replace(",", ".");
                    BigDecimal valor = new BigDecimal(valorNormalizado);
                    log.debug("💵 Valor extraído: {} -> {}", valorStr, valor);
                    return valor;
                } catch (NumberFormatException e) {
                    log.warn("⚠️ Erro ao converter valor: {}", valorStr);
                }
            }
        }
        return null;
    }
}
