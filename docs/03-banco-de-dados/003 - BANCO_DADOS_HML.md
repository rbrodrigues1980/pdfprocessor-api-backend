# Banco de Dados de Homologação (pdfprocessor-hml)

## Visão Geral

Para separar o ambiente de **desenvolvimento/homologação** do ambiente de **produção**, foi criado o banco de dados `pdfprocessor-hml` no mesmo cluster MongoDB Atlas (`FenaeProdCluster`). As credenciais de acesso são as mesmas — apenas o nome do banco muda.

| Ambiente          | Banco de Dados       | Uso                              |
|-------------------|----------------------|----------------------------------|
| **Produção**      | `pdfprocessor`       | Dados reais de produção          |
| **Desenvolvimento/HML** | `pdfprocessor-hml` | Desenvolvimento e testes       |

---

## Procedimento de Criação

### 1. Cópia dos dados (via mongosh)

Conecte ao cluster Atlas e execute o script abaixo para clonar todas as coleções do banco de produção para o banco de homologação:

```javascript
// Alterna para o banco de origem
use pdfprocessor

// Define o banco de origem e o nome do destino
var sourceDb = db.getSiblingDB("pdfprocessor");
var targetDbName = "pdfprocessor-hml";

// Itera sobre todas as coleções e copia os dados
sourceDb.getCollectionNames().forEach(function(collName) {
    // Pula coleções de sistema
    if (collName.startsWith("system.")) return;

    print("Copiando coleção: " + collName + "...");
    
    // Usa o aggregation pipeline com $out para clonar os dados para o novo banco
    sourceDb.getCollection(collName).aggregate([
        { $out: { db: targetDbName, coll: collName } }
    ]);
});

print("Cópia concluída com sucesso!");
```

**Coleções copiadas:**
- `users`
- `system_config`
- `ir_tabela_tributacao`
- `ir_parametros_anuais`
- `payroll_entries`
- `selic_mensal`
- `rubricas`
- `fs.chunks`
- `fs.files`
- `logs`
- `payroll_documents`
- `persons`
- `tenants`
- `taxa_selic`

### 2. Cópia dos índices

Após copiar os dados, execute o script abaixo para recriar todos os índices no banco de destino:

```javascript
var sourceDb = db.getSiblingDB("pdfprocessor");
var targetDb = db.getSiblingDB("pdfprocessor-hml");

sourceDb.getCollectionNames().forEach(function(collName) {
    // Ignora coleções de sistema
    if (collName.startsWith("system.")) return;

    var sourceColl = sourceDb.getCollection(collName);
    var targetColl = targetDb.getCollection(collName);
    
    var indexes = sourceColl.getIndexes();
    
    if (indexes.length > 1) {
        print("Recriando índices para a coleção: " + collName);
    }

    indexes.forEach(function(indexSpec) {
        // O índice padrão _id_ já é criado automaticamente
        if (indexSpec.name === "_id_") return;

        var key = indexSpec.key;
        var options = {};

        // Copia propriedades importantes (unique, sparse, expireAfterSeconds, etc.)
        for (var field in indexSpec) {
            if (field !== "key" && field !== "ns" && field !== "v") {
                options[field] = indexSpec[field];
            }
        }

        print(" -> Criando índice: " + indexSpec.name);
        
        try {
            targetColl.createIndex(key, options);
        } catch (e) {
            print(" [ERRO] Falha ao criar índice " + indexSpec.name + ": " + e);
        }
    });
});

print("Processo de clonagem de índices finalizado!");
```

**Índices recriados:**

| Coleção             | Índice                      |
|---------------------|-----------------------------|
| `users`             | `email_1`                   |
| `rubricas`          | `codigo_1`                  |
| `fs.chunks`         | `files_id_1_n_1`            |
| `fs.files`          | `filename_1_uploadDate_1`   |
| `logs`              | `timestamp_1`               |
| `payroll_documents` | `cpf_1`                     |
| `payroll_documents` | `tipo_1`                    |
| `payroll_documents` | `status_1`                  |
| `payroll_documents` | `dataUpload_-1`             |
| `payroll_documents` | `tenantId_1_fileHash_1`     |
| `payroll_documents` | `tenantId_1`                |
| `persons`           | `tenantId_1`                |
| `persons`           | `tenantId_1_cpf_1`          |

---

## Configuração da Aplicação

### Regra de ouro

| Arquivo            | Vai pro Git? | Banco apontado       | Motivo                                       |
|--------------------|--------------|----------------------|----------------------------------------------|
| `.env.example`     | **Sim**      | `pdfprocessor`       | Referência para deploy/produção              |
| `.env`             | **Não**      | `pdfprocessor-hml`   | Desenvolvimento local (gitignored)           |
| `application.yml`  | **Sim**      | *(variável de ambiente)* | Sem banco hardcoded — usa `${SPRING_DATA_MONGODB_URI}` |
| `docker-compose.yml` | **Sim**   | `pdfprocessor-hml`   | Docker-compose é para dev local              |

### Fluxo por ambiente

```
┌─────────────────────────────────────────────────────────────────┐
│ DESENVOLVIMENTO LOCAL                                           │
│                                                                 │
│  .env (gitignored) ──► SPRING_DATA_MONGODB_URI                  │
│     └── .../pdfprocessor-hml?...                                │
│                                                                 │
│  O dev copia .env.example → .env e troca para pdfprocessor-hml  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ PRODUÇÃO (Cloud Run / Deploy via Git)                           │
│                                                                 │
│  Variável de ambiente no Cloud Run ──► SPRING_DATA_MONGODB_URI  │
│     └── .../pdfprocessor?...                                    │
│                                                                 │
│  .env.example serve como referência — banco "pdfprocessor"      │
└─────────────────────────────────────────────────────────────────┘
```

### Variável de ambiente (.env local)

Para desenvolvimento local, o `.env` deve apontar para o banco `pdfprocessor-hml`:

```
SPRING_DATA_MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/pdfprocessor-hml?retryWrites=true&w=majority
```

### .env.example (commitado no Git)

O `.env.example` aponta para o banco de **produção** (`pdfprocessor`), pois serve como referência para deploy:

```
SPRING_DATA_MONGODB_URI=mongodb+srv://user:password@SEU-CLUSTER.mongodb.net/pdfprocessor?retryWrites=true&w=majority
```

### application.yml

A configuração do `application.yml` usa variável de ambiente, portanto **não precisa ser alterada**:

```yaml
spring:
  data:
    mongodb:
      uri: ${SPRING_DATA_MONGODB_URI}
```

### docker-compose.yml

O `docker-compose.yml` aponta para `pdfprocessor-hml` como banco padrão, pois é usado exclusivamente para desenvolvimento local.

---

## Observações Importantes

1. **Nunca commite credenciais** — o arquivo `.env` está no `.gitignore`.
2. **Produção usa `pdfprocessor`** — ao fazer deploy em Cloud Run, a variável `SPRING_DATA_MONGODB_URI` deve apontar para o banco `pdfprocessor` (e não `pdfprocessor-hml`). O `.env.example` já reflete isso.
3. **Desenvolvimento local usa `pdfprocessor-hml`** — o `.env` local (não commitado) aponta para o banco de homologação.
4. **Mesmo cluster, mesmas credenciais** — ambos os bancos estão no mesmo cluster Atlas (`FenaeProdCluster`) e usam o mesmo usuário MongoDB.
5. **Dados iniciais são cópia de produção** — o banco `pdfprocessor-hml` foi criado como snapshot do banco de produção em 15/02/2026. A partir deste ponto, os dados evoluem independentemente.

---

*Documento criado em: 15/02/2026*
