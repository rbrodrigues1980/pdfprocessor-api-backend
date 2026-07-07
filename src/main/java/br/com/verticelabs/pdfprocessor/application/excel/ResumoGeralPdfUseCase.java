package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.infrastructure.excel.ResumoGeralPdfGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ResumoGeralPdfUseCase {

    private final ResumoGeralUseCase resumoGeralUseCase;
    private final ResumoGeralPdfGenerator pdfGenerator;

    public Mono<ResumoGeralPdfResult> exportByPersonId(String personId) {
        return resumoGeralUseCase.montarByPersonId(personId)
                .flatMap(bundle -> {
                    if (bundle.montagem().linhas() == null || bundle.montagem().linhas().isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.fromCallable(() -> {
                        byte[] bytes = pdfGenerator.generate(bundle.person(), bundle.montagem());
                        String filename = pdfGenerator.buildFilename(bundle.person());
                        return new ResumoGeralPdfResult(bytes, filename);
                    });
                });
    }
}
