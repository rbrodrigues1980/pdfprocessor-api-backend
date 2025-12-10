# PDF Processor API Backend

Bem-vindo ao projeto **PDF Processor API Backend**! Este projeto foi refatorado para seguir os princípios da **Clean Architecture**, oferecendo uma estrutura robusta, escalável e testável para processamento de documentos e autenticação segura.

## 🚀 Tecnologias Utilizadas

Este projeto utiliza uma stack moderna para alta performance e segurança:

*   **Core**:
    *   **Java 21**: Versão LTS mais recente.
    *   **Kotlin**: Usado em conjunto com Java.
    *   **Spring Boot 3.3.x**: Framework principal.
    *   **Spring WebFlux**: Arquitetura reativa não bloqueante.
*   **Banco de Dados**:
    *   **MongoDB Atlas**: Banco de dados NoSQL na nuvem.
    *   **Spring Data MongoDB Reactive**: Conexão reativa.
*   **Arquitetura**:
    *   **Clean Architecture**: Divisão em camadas (Domain, Application, Infrastructure, Interfaces).
*   **Processamento de Arquivos**:
    *   **Apache PDFBox** e **Apache Tika**: Extração de texto e metadados de PDFs.
    *   **Apache POI**: Geração de planilhas Excel (.xlsx).
*   **Segurança**:
    *   **Spring Security WebFlux**: Proteção da API.
    *   **JWT (JSON Web Token)**: Autenticação stateless com Access e Refresh Tokens.
    *   **Argon2**: Hashing de senhas seguro (via Bouncy Castle).
    *   **Multi-tenancy**: Sistema completo de isolamento de dados por tenant (empresa).
*   **Ferramentas**:
    *   **Gradle (Kotlin DSL)**: Build system.
    *   **Lombok**: Redução de boilerplate.
    *   **SpringDoc OpenAPI**: Documentação Swagger automática.
    *   **Logback**: Sistema de logs profissional com rotação automática.

---

## 📂 Estrutura do Projeto (Clean Architecture)

O código está organizado para separar responsabilidades e isolar o domínio:

*   `src/main/java/br/com/verticelabs/pdfprocessor`:
    *   **domain**: O coração do projeto. Contém as Entidades (`User`, `Document`, `Rubrica`) e interfaces de Repositórios/Serviços. **Não depende de frameworks**.
    *   **application**: Casos de uso da aplicação (`AuthUseCase`, `DocumentUseCase`). Orquestra a lógica de negócios.
    *   **infrastructure**: Implementações concretas. Configurações (`SecurityConfig`), adaptadores de banco (`MongoRepository`), serviços de terceiros (`PdfService`, `ExcelService`).
    *   **interfaces**: Camada de entrada. Controladores REST (`AuthController`, `DocumentController`) e DTOs.

---

## ⚙️ Configuração

### 1. Banco de Dados (MongoDB)
O projeto conecta ao MongoDB Atlas. A URI de conexão está em `src/main/resources/application.yml`.

### 2. Segurança (JWT)
As chaves de segurança também estão configuradas no `application.yml`.
**Importante**: Em produção, substitua os valores padrão por variáveis de ambiente.

```yaml
jwt:
  secret: <sua-chave-secreta-super-segura>
  expiration: 900000 # 15 minutos
  refresh-expiration: 2592000000 # 30 dias
```

---

## ▶️ Como Rodar a Aplicação

### Pré-requisitos
*   Java JDK 21 instalado.

### Executando via Terminal
O projeto usa o **Gradle Wrapper**, não é necessário instalar o Gradle manualmente.

1.  **Windows**:
    ```powershell
    .\gradlew.bat bootRun
    ```
2.  **Linux/Mac**:
    ```bash
    ./gradlew bootRun
    ```

A aplicação iniciará na porta **8081**.

---

## 📚 Documentação

### Documentação Completa das APIs e Arquitetura

👉 **[📖 Documentação Completa das APIs e Clean Architecture](./docs/API_COMPLETA_E_ARQUITETURA.md)**

Esta documentação inclui:
- ✅ **Todas as APIs** do projeto com exemplos detalhados
- ✅ **Guia completo da Clean Architecture** explicando cada camada
- ✅ **Comparação MVC vs Clean Architecture**
- ✅ **Mapeamento de componentes** (onde está cada coisa)
- ✅ **Fluxos de dados** e exemplos práticos

### Documentações Específicas para Frontend

- 👉 **[🔐 API de Autenticação](./docs/API_AUTH_FRONTEND.md)** - Guia completo de autenticação JWT, 2FA, refresh tokens
- 👉 **[📄 API de Documentos](./docs/API_DOCUMENTS_FRONTEND.md)** - Upload, processamento e gerenciamento de documentos PDF com isolamento multi-tenant
- 👉 **[🏢 API de Tenants](./docs/API_TENANTS_FRONTEND.md)** - Gerenciamento de tenants (empresas) com exemplos em React, Vue e Angular
- 👉 **[🏷️ API de Rubricas](./docs/API_RUBRICAS_FRONTEND.md)** - Gerenciamento de rubricas (tabela mestra) com isolamento multi-tenant
- 👉 **[👥 API de Pessoas](./docs/API_PERSONS_FRONTEND.md)** - Gerenciamento de pessoas com CRUD completo
- 👉 **[👥 API de Gestão de Pessoas - Implementação](./docs/API_PERSONS_CRUD_IMPLEMENTATION.md)** - Documentação técnica da implementação do CRUD de pessoas
- 👉 **[🔍 API de Extração de Texto](./docs/API_TEXT_EXTRACTION.md)** - Extração de texto de PDFs escaneados usando Tesseract

### Guias Explicativos

- 👉 **[📋 Organização das APIs: Auth, Usuários e Tenants](./docs/ORGANIZACAO_APIS_AUTH_USUARIOS_TENANTS.md)** - Entenda a estrutura e separação das APIs de autenticação, criação de usuários e gerenciamento de tenants
- 👉 **[📋 Planejamento: Gerenciamento Completo de Usuários](./docs/PLANEJAMENTO_GERENCIAMENTO_USUARIOS.md)** - Planejamento detalhado para implementação de CRUD completo de usuários com permissões por role

### Documentação Interativa (Swagger)

Acesse a interface interativa para testar os endpoints:

👉 **[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)**

### Principais Endpoints:

*   **Autenticação**:
    *   `POST /api/v1/auth/login`: Login (retorna Access e Refresh Token).
*   **Pessoas**:
    *   `POST /api/v1/persons`: Criar pessoa.
    *   `GET /api/v1/persons`: Listar pessoas (com paginação e filtros).
    *   `GET /api/v1/persons/{id}`: Buscar pessoa por ID.
    *   `PUT /api/v1/persons/{id}`: Atualizar pessoa.
    *   `DELETE /api/v1/persons/{id}`: Excluir pessoa definitivamente.
    *   `PATCH /api/v1/persons/{id}/activate`: Ativar pessoa.
    *   `PATCH /api/v1/persons/{id}/deactivate`: Desativar pessoa.
*   **Documentos**:
    *   `POST /api/v1/documents/upload`: Upload de PDF (Multipart).
    *   `POST /api/v1/documents/bulk-upload`: Upload múltiplo de PDFs.
    *   `GET /api/v1/documents`: Listar documentos processados.

---

## 📝 Sistema de Logs

O projeto possui um sistema de logs profissional configurado com **Logback**:

*   **Localização**: Os logs são salvos em `logs/fulllog.log`
*   **Formato**: Mesmo formato do console (ISO 8601 com timezone)
*   **Rotação Automática**: 
    *   Cada arquivo tem no máximo **10MB**
    *   Mantém até **5 arquivos** históricos
    *   Quando o 6º arquivo é criado, o mais antigo é removido automaticamente
*   **Estrutura dos arquivos**:
    *   `fulllog.log` (arquivo atual)
    *   `fulllog-YYYY-MM-DD.0.log` (arquivos históricos)

Os logs continuam sendo exibidos no console e também são salvos no arquivo simultaneamente.

## 🏢 Multi-tenancy

O sistema possui suporte completo a **multi-tenancy**, permitindo isolamento total de dados por empresa (tenant):

*   **Isolamento de dados**: Cada tenant possui seus próprios documentos, pessoas e entradas
*   **Roles de usuário**:
    *   `SUPER_ADMIN`: Acesso global, pode gerenciar todos os tenants
    *   `TENANT_ADMIN`: Administrador de uma empresa específica
    *   `TENANT_USER`: Usuário comum de uma empresa
*   **Contexto de tenant**: Resolvido automaticamente via JWT ou header `X-Tenant-ID` (apenas para SUPER_ADMIN)
*   **Índices únicos por tenant**: CPF e hash de arquivo são únicos apenas dentro do mesmo tenant

## 🐛 Solução de Problemas

*   **Erro de Build (Lombok)**: Se tiver problemas com o Lombok, tente rodar `.\gradlew.bat clean build`. O projeto usa uma versão específica do Lombok configurada no Gradle.
*   **Erro de Conexão Mongo**: Verifique se sua rede permite conexão com o MongoDB Atlas (algumas redes corporativas bloqueiam).
*   **Porta já em uso**: Se a porta 8081 estiver ocupada, altere em `src/main/resources/application.yml` na propriedade `server.port`.

---

Bom código! 🚀
#   p d f p r o c e s s o r - a p i - b a c k e n d  
 