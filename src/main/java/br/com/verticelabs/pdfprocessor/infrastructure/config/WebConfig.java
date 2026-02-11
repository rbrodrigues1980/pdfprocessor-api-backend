package br.com.verticelabs.pdfprocessor.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void configurePathMatching(PathMatchConfigurer configurer) {
        // Adiciona o prefixo da versão atual da API apenas para classes do pacote do projeto
        // Exemplo: /documents -> /api/v1/documents
        configurer.addPathPrefix(ApiVersion.PREFIX, c -> c.isAnnotationPresent(RestController.class)
                && c.getPackageName().startsWith("br.com.verticelabs"));
    }
}
