# ============================================
# Stage 1: Build
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Instalar dependências de build
RUN apk add --no-cache bash

# Copiar arquivos de configuração do Gradle
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Dar permissão de execução ao gradlew
RUN chmod +x gradlew

# Baixar dependências (cache layer)
RUN ./gradlew dependencies --no-daemon || true

# Copiar código fonte
COPY src src

# Build do projeto (sem rodar testes para acelerar)
RUN ./gradlew bootJar --no-daemon -x test

# ============================================
# Stage 2: Runtime (sem Tesseract OCR)
# ============================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Instalar apenas dependências necessárias para fontes
RUN apk add --no-cache \
    fontconfig \
    ttf-dejavu \
    && rm -rf /var/cache/apk/*

# Criar diretório para logs
RUN mkdir -p /app/logs

# Copiar JAR do stage de build
COPY --from=builder /app/build/libs/*.jar app.jar

# Criar usuário não-root para segurança
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

USER appuser

# Expor porta padrão (Cloud Run injeta PORT; a app escuta em ${PORT:8081})
EXPOSE 8081

# Health check (usado pelo Docker local; Cloud Run ignora e usa seu próprio mecanismo)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8081}/actuator/health || exit 1

# Configurações JVM otimizadas para container
# NOTA: NÃO definir spring.profiles.active aqui via -D (system property tem prioridade
# sobre env var e impediria trocar o perfil no Cloud Run). Use a env var SPRING_PROFILES_ACTIVE.
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Perfil padrão para Docker local; Cloud Run sobrescreve com SPRING_PROFILES_ACTIVE=prod
ENV SPRING_PROFILES_ACTIVE=docker

# Entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
