package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.service.DocumentTypeDetectionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DocumentTypeDetectionServiceImpl implements DocumentTypeDetectionService {

    @Override
    public Mono<DocumentType> detectType(String pdfText) {
        if (pdfText == null || pdfText.isEmpty()) {
            return Mono.just(DocumentType.CAIXA); // Default
        }
        
        String upperText = pdfText.toUpperCase();
        
        // ===== PADRÕES ESPECÍFICOS DA CAIXA =====
        // 1. Título do documento CAIXA
        boolean hasCaixaTitle = upperText.contains("DEMONSTRATIVO DE PAGAMENTO");
        
        // 2. Logo/Nome da CAIXA
        boolean hasCaixaLogo = upperText.contains("CAIXA ECONÔMICA FEDERAL") ||
                               upperText.contains("CAIXA ECONOMICA FEDERAL");
        
        // 3. Campo "Mês/Ano de Pagamento" (específico da CAIXA)
        boolean hasCaixaDateField = upperText.contains("MÊS/ANO DE PAGAMENTO") ||
                                    upperText.contains("MES/ANO DE PAGAMENTO");
        
        // 4. Campo "Agência" seguido de número (ex: "Agência 2789")
        boolean hasCaixaAgencia = (upperText.contains("AGÊNCIA") || upperText.contains("AGENCIA")) &&
                                  upperText.matches(".*AG[ÊE]NCIA\\s+\\d{4}.*");
        
        // 5. Campo "Sigla GIREC" (específico da CAIXA)
        boolean hasCaixaSigla = upperText.contains("SIGLA") && upperText.contains("GIREC");
        
        // 6. Campo "Operação" com número (ex: "Operação 001")
        boolean hasCaixaOperacao = upperText.contains("OPERAÇÃO") || upperText.contains("OPERACAO");
        
        // CAIXA detectado se tiver pelo menos 2 desses padrões específicos
        boolean hasCaixa = (hasCaixaTitle ? 1 : 0) +
                          (hasCaixaLogo ? 1 : 0) +
                          (hasCaixaDateField ? 1 : 0) +
                          (hasCaixaAgencia ? 1 : 0) +
                          (hasCaixaSigla ? 1 : 0) +
                          (hasCaixaOperacao ? 1 : 0) >= 2;
        
        // ===== PADRÕES ESPECÍFICOS DA FUNCEF =====
        // 1. Título do documento FUNCEF
        boolean hasFuncefTitle = upperText.contains("DEMONSTRATIVO DE PROVENTOS PREVIDENCIÁRIOS") ||
                                 upperText.contains("DEMONSTRATIVO DE PROVENTOS PREVIDENCIARIOS");
        
        // 2. Logo/Nome da FUNCEF
        boolean hasFuncefLogo = upperText.contains("FUNCEF") &&
                               (upperText.contains("FUNDAÇÃO DOS ECONOMIÁRIOS FEDERais") ||
                                upperText.contains("FUNDACAO DOS ECONOMIARIOS FEDERais"));
        
        // 3. Campo "Ano Pagamento / Mês" (específico da FUNCEF)
        boolean hasFuncefDateField = upperText.contains("ANO PAGAMENTO / MÊS") ||
                                    upperText.contains("ANO PAGAMENTO / MES");
        
        // 4. Campo "Nº Benefício INSS"
        boolean hasFuncefBeneficio = upperText.contains("Nº BENEFÍCIO INSS") ||
                                     upperText.contains("Nº BENEFICIO INSS") ||
                                     upperText.contains("N° BENEFÍCIO INSS");
        
        // 5. Campo "Tipo Benefício"
        boolean hasFuncefTipoBeneficio = upperText.contains("TIPO BENEFÍCIO") ||
                                         upperText.contains("TIPO BENEFICIO");
        
        // FUNCEF detectado se tiver pelo menos 2 desses padrões específicos
        boolean hasFuncef = (hasFuncefTitle ? 1 : 0) +
                           (hasFuncefLogo ? 1 : 0) +
                           (hasFuncefDateField ? 1 : 0) +
                           (hasFuncefBeneficio ? 1 : 0) +
                           (hasFuncefTipoBeneficio ? 1 : 0) >= 2;
        
        // ===== DECISÃO FINAL =====
        if (hasCaixa && hasFuncef) {
            return Mono.just(DocumentType.CAIXA_FUNCEF);
        } else if (hasCaixa) {
            return Mono.just(DocumentType.CAIXA);
        } else if (hasFuncef) {
            return Mono.just(DocumentType.FUNCEF);
        }
        
        // Se não encontrou padrões específicos, tenta padrões genéricos
        boolean hasCaixaGeneric = upperText.contains("CONTRACHEQUE") || hasCaixaLogo;
        boolean hasFuncefGeneric = upperText.contains("PREVIDENCIÁRIOS") || 
                                  upperText.contains("PREVIDENCIARIOS") ||
                                  (upperText.contains("FUNCEF") && !hasCaixa);
        
        if (hasCaixaGeneric && hasFuncefGeneric) {
            return Mono.just(DocumentType.CAIXA_FUNCEF);
        } else if (hasCaixaGeneric) {
            return Mono.just(DocumentType.CAIXA);
        } else if (hasFuncefGeneric) {
            return Mono.just(DocumentType.FUNCEF);
        }
        
        // Default para CAIXA se não encontrar nada
        return Mono.just(DocumentType.CAIXA);
    }
}

