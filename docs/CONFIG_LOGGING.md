# Configuração de Logs no MongoDB

Este documento explica como configurar, ativar e desativar o salvamento de logs da aplicação no MongoDB.

## Visão Geral

A aplicação está configurada para salvar logs importantes (níveis INFO, WARN, ERROR) em uma coleção do MongoDB chamada `logs`.

- **Retenção**: Os logs são mantidos por **30 dias**. Após esse período, são automaticamente excluídos pelo MongoDB.
- **Performance**: O envio de logs é assíncrono, ou seja, não impacta o tempo de resposta da API.

## Como Ativar ou Desativar

O controle é feito através do arquivo de configuração `application.yml`.

Arquivo: `src/main/resources/application.yml`

### Para Ativar (Padrão)
Defina a propriedade `enabled` como `true`:

```yaml
app:
  logging:
    mongo:
      enabled: true
```

### Para Desativar
Defina a propriedade `enabled` como `false`. Isso fará com que a aplicação **não** tente conectar ao MongoDB para salvar logs.

```yaml
app:
  logging:
    mongo:
      enabled: false
```

> **Nota**: Ao alterar esse arquivo, é necessário reiniciar a aplicação para que a mudança tenha efeito.

## Verificando os Logs

Se estiver ativado, você pode consultar os logs diretamente no MongoDB:

```javascript
// Exemplo de consulta no Mongo Shell ou Compass
use pdfprocessor
db.logs.find().sort({timestamp: -1}).limit(10)
```

Cada registro de log contém:
- `timestamp`: Data e hora do evento.
- `level`: Nível do log (INFO, ERROR, etc).
- `logger`: Classe que gerou o log.
- `message`: A mensagem do log.
- `context`: Dados adicionais (se houver).
