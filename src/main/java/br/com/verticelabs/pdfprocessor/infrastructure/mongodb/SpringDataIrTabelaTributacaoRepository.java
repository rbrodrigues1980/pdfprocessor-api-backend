package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataIrTabelaTributacaoRepository extends ReactiveMongoRepository<IrTabelaTributacao, String> {

    Flux<IrTabelaTributacao> findByAnoCalendarioAndTipoIncidenciaOrderByFaixa(Integer anoCalendario,
            String tipoIncidencia);

    Mono<Void> deleteByAnoCalendarioAndTipoIncidencia(Integer anoCalendario, String tipoIncidencia);
}
