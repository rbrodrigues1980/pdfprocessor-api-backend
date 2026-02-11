package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataIrParametrosAnuaisRepository extends ReactiveMongoRepository<IrParametrosAnuais, String> {

    Mono<IrParametrosAnuais> findByAnoCalendarioAndTipoIncidencia(Integer anoCalendario, String tipoIncidencia);

    Mono<Void> deleteByAnoCalendarioAndTipoIncidencia(Integer anoCalendario, String tipoIncidencia);
}
