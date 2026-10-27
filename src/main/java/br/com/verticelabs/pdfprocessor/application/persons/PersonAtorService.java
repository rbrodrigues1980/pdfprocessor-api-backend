package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Grava o nome do usuário autenticado no cadastro e na última alteração do cliente.
 * Se não houver sessão, o cliente segue sem esse nome.
 */
@Service
@RequiredArgsConstructor
public class PersonAtorService {

    private final UserRepository userRepository;

    public Mono<Person> registrarCriacao(Person person) {
        return nomeDoUsuarioAtual()
                .doOnNext(nome -> {
                    person.setCriadoPorNome(nome);
                    person.setAtualizadoPorNome(nome);
                })
                .thenReturn(person);
    }

    public Mono<Person> registrarAtualizacao(Person person) {
        return nomeDoUsuarioAtual()
                .doOnNext(person::setAtualizadoPorNome)
                .thenReturn(person);
    }

    private Mono<String> nomeDoUsuarioAtual() {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userRepository::findById)
                .map(User::getNome)
                .filter(nome -> nome != null && !nome.isBlank())
                .map(String::trim)
                .onErrorResume(ex -> Mono.empty());
    }
}
