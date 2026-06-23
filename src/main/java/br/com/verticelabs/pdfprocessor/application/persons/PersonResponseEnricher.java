package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaPercentualHelper;
import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.model.EmpresaPercentual;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import br.com.verticelabs.pdfprocessor.interfaces.persons.PersonMapper;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.PersonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonResponseEnricher {

    private final EmpresaRepository empresaRepository;
    private final PersonMapper personMapper;
    private final EmpresaPercentualHelper percentualHelper;

    public Mono<PersonResponse> enrich(Person person) {
        PersonResponse base = personMapper.toResponse(person);
        if (person.getEmpresaId() == null || person.getEmpresaId().isBlank()) {
            return Mono.just(base);
        }
        return empresaRepository.findById(person.getEmpresaId())
                .map(empresa -> applyEmpresa(base, empresa, person.getPercentualHonorarioId()))
                .defaultIfEmpty(base);
    }

    public Mono<List<PersonResponse>> enrichAll(List<Person> persons) {
        if (persons.isEmpty()) {
            return Mono.just(List.of());
        }
        Set<String> empresaIds = persons.stream()
                .map(Person::getEmpresaId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (empresaIds.isEmpty()) {
            return Mono.just(persons.stream().map(personMapper::toResponse).toList());
        }

        return empresaRepository.findAllById(empresaIds)
                .collectMap(Empresa::getId)
                .map(empresaMap -> persons.stream()
                        .map(person -> {
                            PersonResponse response = personMapper.toResponse(person);
                            Empresa empresa = empresaMap.get(person.getEmpresaId());
                            if (empresa != null) {
                                applyEmpresa(response, empresa, person.getPercentualHonorarioId());
                            }
                            return response;
                        })
                        .toList());
    }

    private PersonResponse applyEmpresa(PersonResponse response, Empresa empresa, String percentualId) {
        response.setEmpresaId(empresa.getId());
        response.setEmpresaNome(empresa.getNome());
        response.setEmpresaSigla(empresa.getSigla());
        percentualHelper.findPercentual(empresa, percentualId).ifPresent(p -> {
            response.setPercentualHonorarios(p.getPercentual());
            response.setPercentualDescricao(p.getDescricao());
            response.setPercentualHonorarioId(p.getId());
        });
        return response;
    }
}
