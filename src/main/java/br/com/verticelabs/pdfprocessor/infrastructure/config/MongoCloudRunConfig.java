package br.com.verticelabs.pdfprocessor.infrastructure.config;

import com.mongodb.connection.ConnectionPoolSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuração do MongoDB otimizada para Cloud Run (serverless).
 *
 * <h3>Problemas resolvidos:</h3>
 *
 * <p><b>1. Conexões stale (MongoSocketWriteException / SSLEngine closed)</b></p>
 * <p>Cloud Run congela instâncias ociosas (CPU = 0). Durante o congelamento, o MongoDB Atlas
 * fecha conexões TCP ociosas silenciosamente (~5 min). Quando a instância descongela,
 * o driver tenta reusar conexões mortas.</p>
 *
 * <p><b>2. Manutenção do Atlas (MongoNodeIsRecoveringException / ShutdownInProgress)</b></p>
 * <p>O Atlas faz rolling restarts periódicos nos nós do cluster. Com {@code minSize=0},
 * a thread de manutenção do pool não tenta criar conexões proativamente, evitando
 * conectar em nós que estão desligando. O {@code serverSelectionTimeout} garante
 * failover rápido para um nó saudável.</p>
 *
 * <h3>Propriedades configuráveis (application.yml ou env vars):</h3>
 * <ul>
 *   <li>{@code mongodb.pool.max-size} — Máximo de conexões no pool (default: 5)</li>
 *   <li>{@code mongodb.pool.min-size} — Mínimo de conexões mantidas vivas (default: 0)</li>
 *   <li>{@code mongodb.pool.max-idle-time-ms} — Tempo máximo ocioso antes de descartar (default: 30s)</li>
 *   <li>{@code mongodb.pool.max-life-time-ms} — Tempo máximo de vida de uma conexão (default: 5 min)</li>
 *   <li>{@code mongodb.pool.max-connecting} — Conexões simultâneas sendo criadas (default: 2)</li>
 *   <li>{@code mongodb.server-selection-timeout-ms} — Timeout para selecionar nó saudável (default: 5s)</li>
 *   <li>{@code mongodb.socket-timeout-ms} — Timeout de leitura/escrita no socket (default: 30s)</li>
 *   <li>{@code mongodb.connect-timeout-ms} — Timeout para abrir conexão TCP (default: 5s)</li>
 * </ul>
 */
@Configuration
public class MongoCloudRunConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoCloudRunConfig.class);

    // --- Connection Pool ---
    @Value("${mongodb.pool.max-size:5}")
    private int maxSize;

    @Value("${mongodb.pool.min-size:0}")
    private int minSize;

    @Value("${mongodb.pool.max-idle-time-ms:30000}")
    private long maxIdleTimeMs;

    @Value("${mongodb.pool.max-life-time-ms:300000}")
    private long maxLifeTimeMs;

    @Value("${mongodb.pool.max-connecting:2}")
    private int maxConnecting;

    // --- Timeouts ---
    @Value("${mongodb.server-selection-timeout-ms:5000}")
    private long serverSelectionTimeoutMs;

    @Value("${mongodb.socket-timeout-ms:30000}")
    private long socketTimeoutMs;

    @Value("${mongodb.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Bean
    public MongoClientSettingsBuilderCustomizer cloudRunConnectionPoolCustomizer() {
        return builder -> {
            builder.applyToConnectionPoolSettings(pool -> {
                pool.maxSize(maxSize)
                    .minSize(minSize)
                    .maxConnectionIdleTime(maxIdleTimeMs, TimeUnit.MILLISECONDS)
                    .maxConnectionLifeTime(maxLifeTimeMs, TimeUnit.MILLISECONDS)
                    .maxConnecting(maxConnecting);

                logPoolSettings(pool.build());
            });

            builder.applyToClusterSettings(cluster ->
                cluster.serverSelectionTimeout(serverSelectionTimeoutMs, TimeUnit.MILLISECONDS)
            );

            builder.applyToSocketSettings(socket ->
                socket.connectTimeout((int) connectTimeoutMs, TimeUnit.MILLISECONDS)
                      .readTimeout((int) socketTimeoutMs, TimeUnit.MILLISECONDS)
            );

            log.info("MongoDB timeouts configurados: serverSelection={}ms, connect={}ms, socket={}ms",
                    serverSelectionTimeoutMs, connectTimeoutMs, socketTimeoutMs);
        };
    }

    private void logPoolSettings(ConnectionPoolSettings settings) {
        log.info("MongoDB connection pool configurado para Cloud Run: " +
                "maxSize={}, minSize={}, maxIdleTime={}ms, maxLifeTime={}ms, maxConnecting={}",
                settings.getMaxSize(),
                settings.getMinSize(),
                settings.getMaxConnectionIdleTime(TimeUnit.MILLISECONDS),
                settings.getMaxConnectionLifeTime(TimeUnit.MILLISECONDS),
                settings.getMaxConnecting());
    }
}
