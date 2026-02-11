# 📄 DOMAIN_MODELS_MULTI_TENANT.md

## Modelos de Domínio Multi-Tenant — Isolamento Completo Entre Empresas (DDD + Clean Architecture)

Este documento define todos os modelos do domínio utilizados pelo sistema, adaptados para um ambiente multi-tenant, garantindo:

- isolamento total entre empresas
- consistência das regras de negócio
- segurança e escalabilidade
- aderência aos princípios DDD + SOLID + Clean Architecture

Todos os modelos aqui descritos ficam dentro de:

```
domain/
    model/
    repository/
    service/
    exceptions/
```

---

# 1. PRINCÍPIOS DO DOMÍNIO MULTI-TENANT

Antes dos modelos, definimos regras essenciais:

✔ **1.1 Cada entidade pertence a um único tenant**

Todas as entidades possuem obrigatoriamente:

```
tenantId: String
```

✔ **1.2 SUPER_ADMIN pode ignorar tenant**

Tenants diferentes nunca se cruzam.

✔ **1.3 Isolamento por default**

Repos padrões:

- `findByTenantId(...)`
- `findByTenantIdAndId(...)`
- `findAllByTenantId(...)`

✔ **1.4 Entidades não conhecem a persistência**

Seguem 100% Clean Architecture.

---

# 2. MODEL: Tenant (Empresa)

Representa uma empresa que usa o sistema.

```json
{
  "id": "tenant123",
  "nome": "Empresa XPTO",
  "dominio": "xpto.com.br",
  "ativo": true,
  "createdAt": "2025-02-01T12:00:00Z",
  "config": {
    "twoFactorRequired": false,
    "maxUsers": 20
  }
}
```

## 📌 Campos e Regras

| Campo | Tipo | Regra |
|-------|------|-------|
| id | String | UUID gerado pelo sistema |
| nome | String | obrigatório |
| dominio | String | opcional (login por domínio) |
| ativo | Boolean | SUPER_ADMIN controla |
| config.twoFactorRequired | Boolean | força 2FA em todos usuários do tenant |
| config.maxUsers | Int | controle de licenças |

---

# 3. MODEL: User (Vinculado ao Tenant)

Cada usuário pertence a apenas 1 tenant.

```json
{
  "id": "user123",
  "tenantId": "tenant123",
  "nome": "Administrador XPTO",
  "email": "admin@xpto.com.br",
  "senhaHash": "argon2id...",
  "roles": ["TENANT_ADMIN"],

  "twoFactorEnabled": true,
  "twoFactorTempCode": null,
  "twoFactorTempCodeExpires": null,

  "refreshTokens": []
}
```

## 📌 Regras Essenciais

✔ email é globalmente único, independentemente do tenant

✔ user só acessa seu tenant

✔ roles definem permissões:

- SUPER_ADMIN
- TENANT_ADMIN
- TENANT_USER

✔ controle de 2FA individual

✔ múltiplos refresh tokens rotativos

---

# 4. MODEL: Person (Titular dos Contracheques)

Cada Pessoa é sempre propriedade de um tenant.

```json
{
  "tenantId": "tenant123",
  "cpf": "12449709568",
  "nome": "FLAVIO ALMEIDA",
  "documentos": ["doc123", "doc456"]
}
```

## 📌 Regras

| Item | Regra |
|------|-------|
| CPF | único por tenant |
| documentos | lista de documentos pertencentes ao tenant |

---

# 5. MODEL: PayrollDocument (Documento PDF Processado)

Representa cada arquivo PDF enviado por um usuário.

```json
{
  "id": "doc123",
  "tenantId": "tenant123",
  "cpf": "12449709568",
  "tipo": "CAIXA | FUNCEF | MISTO",
  "ano": 2018,
  "status": "PENDING | PROCESSING | PROCESSED | ERROR",
  "pages": [
    { "page": 1, "origem": "CAIXA" },
    { "page": 2, "origem": "FUNCEF" }
  ],
  "createdAt": "2025-01-10T12:00:00Z",
  "uploadedBy": "user123"
}
```

## 📌 Regras

✔ Documentos sempre pertencem a um tenant

✔ O tipo do documento é detectado automaticamente

✔ Status segue workflow:

```
PENDING → PROCESSING → PROCESSED | ERROR
```

---

# 6. MODEL: PayrollEntry (Rubrica Extraída)

Cada linha extraída do PDF vira um PayrollEntry.

```json
{
  "tenantId": "tenant123",
  "documentId": "doc123",
  "rubricaCodigo": "4482",
  "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2015",
  "referencia": "2017-08",
  "valor": 885.47,
  "pagina": 1,
  "origem": "CAIXA"
}
```

## 📌 Regras

- obrigatório: tenantId, documentId, rubricaCodigo, valor
- referência sempre normalizada para yyyy-MM
- ligação forte com rubricas cadastradas
- pertence ao mesmo tenant do documento

---

# 7. MODEL: Rubrica (Global ou por Tenant)

Existem dois modos:

✔ **Modelo 1 — Rubricas Globais (recomendado)**

- Criadas pelo SUPER_ADMIN
- Afetam todos os tenants

✔ **Modelo 2 — Rubricas Customizadas por Tenant**

Cada empresa pode ter rubricas adicionais:

```json
{
  "tenantId": "tenant123",
  "codigo": "9001",
  "descricao": "Verba personalizada XPTO"
}
```

## 📌 Estrutura

```json
{
  "tenantId": "GLOBAL or tenant123",
  "codigo": "4430",
  "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
  "categoria": "Administrativa",
  "ativo": true
}
```

---

# 8. MODEL: Consolidation (Resultado Consolidado)

Gerado ao final do processamento:

```json
{
  "tenantId": "tenant123",
  "cpf": "12449709568",
  "ano": 2018,
  "matriz": [
    {
      "codigo": "4482",
      "janeiro": 885.47,
      "fevereiro": 0,
      ...
      "dezembro": 921.00
    }
  ],
  "generatedAt": "2025-01-11T08:24:00Z"
}
```

**Regras:**

✔ consolidação só pode mostrar dados do tenant

✔ matrizes podem ser regeneradas sob demanda

---

# 9. MODEL: AuditLog (Auditoria)

```json
{
  "tenantId": "tenant123",
  "userId": "user123",
  "timestamp": "2025-01-10T19:25:10Z",
  "evento": "LOGIN_FAILED",
  "detalhes": { "ip": "10.10.10.10" }
}
```

---

# 10. CONTRATOS DO DOMÍNIO (Interfaces)

Exemplos:

## 📌 UserRepository

```java
interface UserRepository {
    fun findByEmail(email: String): Mono<User>
    fun findByTenantIdAndId(tenantId: String, id: String): Mono<User>
    fun save(user: User): Mono<User>
}
```

## 📌 PayrollDocumentRepository

```java
interface PayrollDocumentRepository {
    fun findByTenantIdAndId(tenantId: String, id: String): Mono<PayrollDocument>
    fun findAllByTenantIdAndCpf(tenantId: String, cpf: String): Flux<PayrollDocument>
}
```

---

# 11. AGREGAÇÕES (DDD)

## 📌 Aggregate Roots

| Aggregate | Entities incluídas |
|-----------|-------------------|
| Tenant | TenantConfig |
| User | RefreshTokens, 2FA |
| Person | PayrollDocument, PayrollEntry |
| Rubrica | Global e custom |
| Consolidation | matriz consolidada |

---

# 12. REGRAS DE NEGÓCIO IMPORTANTES

✔ Todo modelo tem tenantId

✔ SUPER_ADMIN pode ignorar tenant

✔ Não existe relação entre tenants diferentes

✔ Rubricas globais + customizadas coexistem

✔ Users são únicos no sistema inteiro

✔ CPF é único por tenant, não global

✔ Uma consolidação só olha dados do tenant

---

# 13. CHECKLIST DE MODELOS (verificação)

| Item | ✔ |
|------|---|
| Todos modelos possuem tenantId | ✔ |
| Modelos seguem DDD | ✔ |
| Campos sensíveis nunca expostos | ✔ |
| Não existe dependência de infraestrutura | ✔ |
| Normalize: datas, valores, rubricas | ✔ |
| Repositórios são interfaces | ✔ |

---

# 14. RECOMENDAÇÃO FINAL

✔ Modelos estão prontos para implementação em Clean Architecture

✔ Multi-Tenancy aplicado corretamente por design

✔ Alinhado com DDD, SOLID e boas práticas SaaS

