# Jornada do Usuário — Guia QA

> Documentação passo a passo de toda a jornada do usuário no sistema,
> desde o login até a exportação final em Excel.
> Escrito como um roteiro de QA para validação funcional.

---

## Sumário

| Etapa | Jornada | Descrição |
|-------|---------|-----------|
| 1 | [Login e Autenticação](#etapa-1--login-e-autenticação) | Acessar o sistema e obter token de acesso |
| 2 | [Tela de Pessoas](#etapa-2--tela-de-pessoas) | Visualizar a lista de pessoas cadastradas |
| 3 | [Cadastrar Nova Pessoa](#etapa-3--cadastrar-nova-pessoa) | Criar um novo cadastro de pessoa |
| 4 | [Upload de Documentos (Contracheques)](#etapa-4--upload-de-documentos-contracheques) | Enviar PDFs de contracheques para uma pessoa |
| 5 | [Processamento dos Documentos](#etapa-5--processamento-dos-documentos) | Processar os PDFs enviados para extração de rubricas |
| 6 | [Visualizar Documentos da Pessoa](#etapa-6--visualizar-documentos-da-pessoa) | Ver lista de documentos enviados e seu status |
| 7 | [Visualizar Entries (Rubricas Extraídas)](#etapa-7--visualizar-entries-rubricas-extraídas) | Ver os lançamentos extraídos de cada documento |
| 8 | [Consolidação (Matriz de Rubricas)](#etapa-8--consolidação-matriz-de-rubricas) | Ver a matriz consolidada ano/mês por rubrica |
| 9 | [Exportar Excel](#etapa-9--exportar-excel) | Gerar planilha Excel com os dados consolidados |
| 10 | [Upload de Declaração de Imposto de Renda](#etapa-10--upload-de-declaração-de-imposto-de-renda) | Enviar PDF de declaração de IR |
| 11 | [Ações da Lista de Pessoas](#etapa-11--ações-da-lista-de-pessoas) | Detalhamento de cada ação disponível na lista |
| 12 | [Editar Pessoa](#etapa-12--editar-pessoa) | Alterar dados cadastrais de uma pessoa |
| 13 | [Desativar / Ativar Pessoa](#etapa-13--desativar--ativar-pessoa) | Desativar ou reativar um cadastro |
| 14 | [Excluir Pessoa](#etapa-14--excluir-pessoa) | Remover definitivamente uma pessoa |
| 15 | [Excluir Documento](#etapa-15--excluir-documento) | Remover um documento específico e seus dados |
| 16 | [Reprocessar Documento](#etapa-16--reprocessar-documento) | Reprocessar um documento com erro ou desatualizado |

---

## Etapa 1 — Login e Autenticação

### Objetivo
Acessar o sistema com credenciais válidas para obter permissão de uso.

### Pré-condições
- Ter um usuário cadastrado no sistema (email + senha)
- O sistema deve estar acessível (backend rodando)

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 1.1 | Abrir a tela de login do sistema | Exibe formulário com campos **Email** e **Senha** |
| 1.2 | Preencher o campo **Email** com um email válido cadastrado | Campo aceita o email |
| 1.3 | Preencher o campo **Senha** com a senha correta | Campo aceita a senha (caracteres mascarados) |
| 1.4 | Clicar no botão **Entrar** | Sistema valida as credenciais |
| 1.5 | **Se 2FA estiver ativado:** Inserir o código de 6 dígitos enviado por email | Sistema valida o código |
| 1.6 | Autenticação bem-sucedida | Usuário é redirecionado para a tela principal. Token de acesso (JWT) é armazenado automaticamente |

### Validações de QA

- [ ] Login com email inválido exibe mensagem de erro
- [ ] Login com senha incorreta exibe mensagem de erro
- [ ] Login com conta desativada exibe mensagem apropriada
- [ ] Token expira em 15 minutos e sistema faz refresh automático
- [ ] Código 2FA incorreto exibe mensagem de erro
- [ ] Após 5 tentativas falhas, conta é bloqueada temporariamente

### Observações
> O token JWT expira em **15 minutos**. O sistema deve fazer refresh automático usando o refresh token (válido por 30 dias) sem que o usuário perceba.

---

## Etapa 2 — Tela de Pessoas

### Objetivo
Visualizar todas as pessoas cadastradas no sistema com seus dados resumidos.

### Pré-condições
- Estar autenticado no sistema (Etapa 1 concluída)

### O que a tela exibe

Ao acessar a tela de **Pessoas**, o sistema apresenta:

| Elemento | Descrição |
|----------|-----------|
| Título | "Pessoas" com subtítulo "Gerenciar pessoas cadastradas no sistema" |
| Botão "Nova Pessoa" | Botão roxo no canto superior direito para criar nova pessoa |
| Campo "Buscar por nome..." | Filtro de busca por nome (parcial, sem diferenciar maiúsculas) |
| Campo "Buscar por CPF..." | Filtro de busca por CPF (parcial) |
| Contador | "X pessoas encontradas" |
| Lista de Pessoas | Tabela com as colunas abaixo |

### Colunas da tabela

| Coluna | Descrição | Exemplo |
|--------|-----------|---------|
| **Nome** | Nome completo da pessoa (com ID parcial abaixo) | FLAVIO JOSE PEREIRA ALMEIDA / ID: 6947e787... |
| **CPF** | CPF formatado com pontos e traço | 124.497.095-68 |
| **Documentos** | Quantidade de documentos enviados (link clicável) | 9 documentos |
| **Data de Criação** | Data em que a pessoa foi cadastrada | 21 de dez. de 2025 |
| **Ações** | Ícones de ação (detalhados na [Etapa 11](#etapa-11--ações-da-lista-de-pessoas)) | Ícones interativos |

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 2.1 | Navegar para a tela de **Pessoas** | Exibe a lista com todas as pessoas do tenant |
| 2.2 | Verificar o contador de pessoas | Mostra o total correto (ex: "74 pessoas encontradas") |
| 2.3 | Digitar um nome no campo "Buscar por nome..." | Lista é filtrada em tempo real conforme o texto digitado |
| 2.4 | Digitar um CPF no campo "Buscar por CPF..." | Lista é filtrada mostrando apenas a pessoa com aquele CPF |
| 2.5 | Limpar os filtros | Lista volta a exibir todas as pessoas |

### Validações de QA

- [ ] A lista carrega corretamente ao acessar a tela
- [ ] Filtro por nome funciona com busca parcial (ex: digitar "FLAVIO" encontra a pessoa)
- [ ] Filtro por CPF funciona com busca parcial (ex: digitar "124" encontra CPFs que começam com 124)
- [ ] Contador de pessoas atualiza ao filtrar
- [ ] Paginação funciona quando há muitas pessoas
- [ ] Usuários de tenants diferentes não veem pessoas de outros tenants

---

## Etapa 3 — Cadastrar Nova Pessoa

### Objetivo
Criar um novo cadastro de pessoa no sistema para posteriormente vincular documentos.

### Pré-condições
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**
- Estar na tela de Pessoas (Etapa 2)

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 3.1 | Clicar no botão **"Nova Pessoa"** (roxo, canto superior direito) | Abre formulário/modal de cadastro |
| 3.2 | Preencher o campo **CPF** (obrigatório) | Campo aceita CPF com ou sem formatação |
| 3.3 | Preencher o campo **Nome** (obrigatório) | Campo aceita o nome completo |
| 3.4 | Preencher o campo **Matrícula** (opcional) | Campo aceita a matrícula (7 dígitos) |
| 3.5 | Clicar no botão **Salvar** / **Cadastrar** | Sistema valida os dados e cria a pessoa |
| 3.6 | Cadastro bem-sucedido | Pessoa aparece na lista. Mensagem de sucesso é exibida |

### Regras de negócio

- O **CPF** é validado com algoritmo da Receita Federal (Mod11)
- O **CPF** é normalizado automaticamente para 11 dígitos (sem pontos e traço)
- O **Nome** é salvo em UPPERCASE
- A **Matrícula** é normalizada para 7 dígitos
- Não é possível cadastrar duas pessoas com o mesmo CPF no mesmo tenant
- A pessoa é criada com status **ativo = true**

### Validações de QA

- [ ] CPF inválido (ex: 111.111.111-11) exibe mensagem de erro
- [ ] CPF duplicado no mesmo tenant exibe erro "Já existe pessoa com este CPF"
- [ ] Nome em branco não permite salvar
- [ ] Pessoa aparece imediatamente na lista após o cadastro
- [ ] CPF é salvo normalizado (apenas números, 11 dígitos)

---

## Etapa 4 — Upload de Documentos (Contracheques)

### Objetivo
Enviar arquivos PDF de contracheques (CAIXA e/ou FUNCEF) para uma pessoa cadastrada.

### Pré-condições
- Pessoa já cadastrada no sistema (Etapa 3 concluída)
- Ter arquivos PDF de contracheques disponíveis
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**

### Caminho A — Upload pela lista de Pessoas (ícone de upload)

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 4.1 | Na lista de Pessoas, localizar a pessoa desejada | Pessoa visível na lista |
| 4.2 | Clicar no **ícone de upload** (↓) nas ações da pessoa | Abre modal/tela de upload de documentos |
| 4.3 | Selecionar um ou mais arquivos PDF | Arquivos são listados para envio |
| 4.4 | Clicar em **Enviar** / **Upload** | Upload é realizado |
| 4.5 | Upload bem-sucedido | Cada documento recebe status **PENDING**. Sistema detecta automaticamente o tipo (CAIXA/FUNCEF/MISTO) |

### Caminho B — Upload pela tela de detalhes da Pessoa

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 4.1 | Acessar os detalhes de uma pessoa (clicar no nome ou ícone de visualização) | Abre tela de detalhes da pessoa |
| 4.2 | Localizar a seção de **Documentos** ou botão **Upload** | Seção de documentos visível |
| 4.3 | Selecionar um ou mais arquivos PDF | Arquivos listados para envio |
| 4.4 | Clicar em **Enviar** / **Upload** | Upload realizado |
| 4.5 | Upload bem-sucedido | Documentos aparecem na lista com status **PENDING** |

### Regras de negócio

- **Formato aceito:** Apenas PDF
- **Tamanho máximo:** 10 MB por arquivo
- **Detecção automática:** O sistema identifica se o contracheque é CAIXA, FUNCEF ou MISTO
- **Prevenção de duplicatas:** O sistema calcula hash SHA-256 de cada arquivo. Se o mesmo arquivo for enviado novamente, retorna erro **409 Conflict**
- **Upload múltiplo:** É possível enviar vários arquivos de uma vez (bulk upload)

### Status após upload

| Status | Significado |
|--------|-------------|
| `PENDING` | Documento enviado com sucesso, aguardando processamento |

### Validações de QA

- [ ] Arquivo não-PDF é rejeitado com mensagem de erro
- [ ] Arquivo maior que 10 MB é rejeitado
- [ ] Arquivo PDF corrompido/inválido é rejeitado
- [ ] Enviar o mesmo arquivo duas vezes retorna erro "Este arquivo já foi enviado anteriormente"
- [ ] Upload múltiplo envia todos os arquivos e reporta sucesso/falha individual
- [ ] Contador de documentos da pessoa é atualizado após upload
- [ ] Tipo do documento (CAIXA/FUNCEF) é detectado corretamente

---

## Etapa 5 — Processamento dos Documentos

### Objetivo
Processar os PDFs enviados para extrair automaticamente as rubricas (lançamentos) de cada contracheque.

### Pré-condições
- Documento com status **PENDING** (Etapa 4 concluída)
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 5.1 | Na lista de documentos da pessoa, localizar um documento com status **PENDING** | Documento visível com status PENDING |
| 5.2 | Clicar no botão/ícone **Processar** | Processamento é iniciado |
| 5.3 | Status muda para **PROCESSING** | Interface exibe indicador de processamento (loading/spinner) |
| 5.4 | Aguardar processamento | O sistema extrai rubricas página por página usando regex (CAIXA) ou IA Gemini (se habilitado) |
| 5.5 | Processamento concluído | Status muda para **PROCESSED**. Número de entries extraídas é exibido |

### Transições de status

```
PENDING → PROCESSING → PROCESSED   (sucesso)
PENDING → PROCESSING → ERROR       (falha na extração)
```

### O que acontece durante o processamento

1. O sistema lê o PDF página por página
2. Detecta o tipo de cada página (CAIXA ou FUNCEF)
3. Aplica padrões regex para extrair rubricas
4. Se a **IA Gemini** estiver habilitada, usa o modelo para extrair dados de PDFs complexos
5. Normaliza valores (datas, monetários, descrições)
6. Valida as rubricas extraídas contra a tabela de rubricas do sistema
7. Cria as **entries** (lançamentos) no banco de dados
8. Atualiza o status do documento

### Validações de QA

- [ ] Documento PENDING pode ser processado
- [ ] Status muda para PROCESSING durante o processamento
- [ ] Status muda para PROCESSED ao concluir com sucesso
- [ ] Status muda para ERROR quando há falha (PDF corrompido, formato não reconhecido)
- [ ] Número de entries extraídas é exibido corretamente
- [ ] Tipo do documento (CAIXA/FUNCEF) é identificado corretamente
- [ ] Documento já em PROCESSING não pode ser processado novamente
- [ ] Se a IA estiver habilitada, o `confidenceScore` é preenchido

---

## Etapa 6 — Visualizar Documentos da Pessoa

### Objetivo
Ver a lista de todos os documentos enviados para uma pessoa, com seus status e detalhes.

### Pré-condições
- Pessoa com documentos já enviados (Etapa 4 concluída)

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 6.1 | Na lista de Pessoas, clicar no **ícone de documentos** (📄) ou no link "X documentos" | Abre a lista de documentos da pessoa |
| 6.2 | Visualizar a lista de documentos | Cada documento exibe: tipo, ano, status, data de upload, entries |

### Informações exibidas por documento

| Campo | Descrição | Exemplo |
|-------|-----------|---------|
| **Tipo** | Tipo do contracheque | CAIXA, FUNCEF, CAIXA_FUNCEF, IRPF |
| **Ano** | Ano de referência do documento | 2018 |
| **Status** | Estado atual do processamento | PENDING, PROCESSING, PROCESSED, ERROR |
| **Entries** | Quantidade de rubricas extraídas | 25 |
| **Confiança IA** | Score de confiança da extração por IA (0.0 a 1.0) | 0.92 |
| **Recomendação** | Recomendação do sistema sobre o resultado | ACCEPT, REVIEW, REJECT |
| **Data Upload** | Quando o documento foi enviado | 15/01/2024 10:30 |
| **Data Processamento** | Quando o processamento foi concluído | 15/01/2024 10:35 |
| **Erro** | Mensagem de erro (se houver) | "Formato não reconhecido" |

### Validações de QA

- [ ] Todos os documentos da pessoa são listados
- [ ] Status é exibido corretamente para cada documento
- [ ] Documentos com erro exibem a mensagem de erro
- [ ] É possível filtrar documentos por status, tipo ou ano
- [ ] Documentos PROCESSED mostram contagem de entries

---

## Etapa 7 — Visualizar Entries (Rubricas Extraídas)

### Objetivo
Ver os lançamentos (rubricas) que foram extraídos de um documento processado.

### Pré-condições
- Documento com status **PROCESSED** (Etapa 5 concluída)

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 7.1 | Na lista de documentos, clicar em um documento com status **PROCESSED** | Abre a lista de entries do documento |
| 7.2 | Visualizar as entries extraídas | Lista com todas as rubricas extraídas do PDF |

### Informações exibidas por entry

| Campo | Descrição | Exemplo |
|-------|-----------|---------|
| **Código da Rubrica** | Código identificador da rubrica | 00010 |
| **Descrição** | Nome da rubrica | VENCIMENTO BASICO |
| **Referência** | Mês/ano de referência | 01/2018 |
| **Valor** | Valor monetário (R$) | 5.234,56 |
| **Origem** | De qual contracheque veio | CAIXA ou FUNCEF |
| **Página** | Em qual página do PDF foi encontrada | 3 |

### Validações de QA

- [ ] Entries são listadas corretamente para o documento
- [ ] Valores monetários estão formatados corretamente (R$)
- [ ] Referência (mês/ano) está correta
- [ ] Origem (CAIXA/FUNCEF) corresponde ao tipo do documento
- [ ] Página está correta em relação ao PDF original
- [ ] Documento sem entries exibe mensagem "Nenhuma rubrica extraída"

---

## Etapa 8 — Consolidação (Matriz de Rubricas)

### Objetivo
Visualizar todos os lançamentos de uma pessoa organizados em uma matriz consolidada (rubricas x meses/anos), com totais por rubrica e por mês.

### Pré-condições
- Pessoa com pelo menos um documento **PROCESSED** (Etapa 5 concluída)

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 8.1 | Na lista de Pessoas ou na tela de detalhes, clicar no **ícone de consolidação** ou acessar a visão consolidada da pessoa | Abre a matriz consolidada |
| 8.2 | Visualizar a matriz | Exibe tabela com rubricas nas linhas e meses/anos nas colunas |
| 8.3 | (Opcional) Filtrar por **ano** | Exibe apenas dados do ano selecionado |
| 8.4 | (Opcional) Filtrar por **origem** (CAIXA ou FUNCEF) | Exibe apenas dados da origem selecionada |

### Estrutura da matriz

```
                    Jan/2017   Fev/2017   Mar/2017   ...   Dez/2019   TOTAL
VENC. BASICO        5.234,56   5.234,56   5.234,56   ...   6.100,00   xxx.xxx,xx
ANUENIO             1.200,00   1.200,00   1.200,00   ...   1.500,00   xxx.xxx,xx
GRATIFICACAO        2.000,00   2.000,00   2.000,00   ...   2.500,00   xxx.xxx,xx
...
TOTAL MENSAL        x.xxx,xx   x.xxx,xx   x.xxx,xx   ...   x.xxx,xx   TOTAL GERAL
```

### Validações de QA

- [ ] Matriz exibe corretamente rubricas nas linhas e meses nas colunas
- [ ] Valores correspondem às entries extraídas dos documentos
- [ ] Totais por rubrica (linha) estão corretos
- [ ] Totais mensais (coluna) estão corretos
- [ ] Total geral está correto
- [ ] Filtro por ano funciona e mostra apenas dados do ano selecionado
- [ ] Filtro por origem (CAIXA/FUNCEF) funciona corretamente
- [ ] Células sem dados aparecem vazias ou com "0,00"

---

## Etapa 9 — Exportar Excel

### Objetivo
Gerar e baixar uma planilha Excel (.xlsx) com os dados consolidados da pessoa.

### Pré-condições
- Pessoa com dados consolidados disponíveis (Etapa 8 concluída)

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 9.1 | Na tela de detalhes da pessoa ou na lista, clicar no **ícone de download/Excel** (↓) | Inicia a geração do arquivo Excel |
| 9.2 | Aguardar a geração | Sistema processa os dados e gera o arquivo |
| 9.3 | Download do arquivo | Arquivo `.xlsx` é baixado automaticamente para o computador |

### Conteúdo do Excel

O arquivo Excel contém 3 abas:

| Aba | Conteúdo |
|-----|----------|
| **Consolidação** | Matriz completa de rubricas x meses/anos com valores |
| **Totais Mensais** | Resumo dos totais por mês |
| **Metadados** | Informações da pessoa (CPF, nome), data de geração, filtros aplicados |

### Estrutura da aba "Consolidação"

- **Coluna A:** Código da rubrica
- **Coluna B:** Descrição da rubrica
- **Colunas C em diante:** Meses (Jan/2017, Fev/2017, ..., até 36 meses)
- **Última coluna:** Total por rubrica
- **Última linha:** Total mensal

### Validações de QA

- [ ] Arquivo Excel é gerado e baixado com sucesso
- [ ] Nome do arquivo contém o CPF ou nome da pessoa
- [ ] Aba "Consolidação" contém a matriz completa
- [ ] Valores no Excel correspondem exatamente aos valores da tela de consolidação
- [ ] Formatação está correta (valores monetários, cabeçalhos)
- [ ] Aba "Metadados" contém CPF, nome e data de geração
- [ ] Arquivo abre corretamente no Microsoft Excel e Google Sheets

---

## Etapa 10 — Upload de Declaração de Imposto de Renda

### Objetivo
Enviar PDF de declaração de Imposto de Renda de uma pessoa para extração de dados fiscais.

### Pré-condições
- Pessoa já cadastrada no sistema
- Ter arquivo PDF da declaração de IR disponível
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 10.1 | Acessar a tela de detalhes da pessoa ou usar o upload por personId | Acessar a opção de upload de IR |
| 10.2 | Selecionar a opção **"Upload Declaração IR"** | Abre seletor de arquivo |
| 10.3 | Selecionar o arquivo PDF da declaração | Arquivo é selecionado |
| 10.4 | Clicar em **Enviar** | Upload é realizado |
| 10.5 | Processamento automático | O sistema extrai automaticamente os dados da declaração usando iText 8 |

### Dados extraídos da declaração de IR

O sistema extrai automaticamente **37 rubricas** organizadas em categorias:

| Categoria | Exemplos de rubricas |
|-----------|---------------------|
| **Dados Básicos** | Nome, CPF, ano do exercício |
| **Imposto Devido** | Imposto devido, imposto retido na fonte |
| **Rendimentos** | Rendimentos tributáveis, isentos |
| **Deduções** | Despesas médicas, educação, previdência |
| **Bens e Direitos** | Total de bens e direitos |
| **Dívidas** | Total de dívidas e ônus |

### Validações de QA

- [ ] Upload de PDF de declaração de IR é aceito
- [ ] Sistema identifica o tipo como "IRPF"
- [ ] Processamento automático extrai as 37 rubricas
- [ ] Dados extraídos estão corretos em relação ao PDF original
- [ ] Formatos 2016 e 2017+ são suportados
- [ ] PDF de declaração duplicada retorna erro 409

---

## Etapa 11 — Ações da Lista de Pessoas

### Objetivo
Detalhar cada ação disponível na coluna "Ações" da lista de pessoas.

### Mapa de ícones (da esquerda para a direita)

| # | Ícone | Ação | Descrição | Role Necessária |
|---|-------|------|-----------|-----------------|
| 1 | ↓ (Download) | **Exportar Excel** | Baixa a planilha Excel consolidada da pessoa | Todos |
| 2 | 👁 (Olho) | **Visualizar Detalhes** | Abre a tela de detalhes da pessoa com documentos, entries e consolidação | Todos |
| 3 | 📋 (Copiar) | **Copiar Dados** | Copia informações da pessoa para a área de transferência | Todos |
| 4 | 📄 (Documento) | **Ver Documentos** | Abre a lista de documentos da pessoa | Todos |
| 5 | ✏️ (Lápis) | **Editar Pessoa** | Abre formulário de edição dos dados cadastrais | TENANT_ADMIN, SUPER_ADMIN |
| 6 | 🚫 (Desativar) | **Desativar Pessoa** | Desativa o cadastro da pessoa (não exclui) | TENANT_ADMIN, SUPER_ADMIN |
| 7 | 🗑️ (Lixeira) | **Excluir Pessoa** | Remove definitivamente a pessoa e todos os seus dados | TENANT_ADMIN, SUPER_ADMIN |

### Validações de QA

- [ ] Todos os ícones são visíveis e clicáveis
- [ ] Ícones de ações administrativas estão desabilitados para TENANT_USER
- [ ] Cada ícone executa a ação correta
- [ ] Tooltip (texto ao passar o mouse) descreve a ação

---

## Etapa 12 — Editar Pessoa

### Objetivo
Alterar dados cadastrais de uma pessoa existente.

### Pré-condições
- Pessoa já cadastrada no sistema
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 12.1 | Na lista de Pessoas, clicar no **ícone de edição** (✏️) da pessoa desejada | Abre formulário de edição com dados atuais preenchidos |
| 12.2 | Alterar o campo **Nome** | Campo aceita o novo nome |
| 12.3 | Alterar o campo **Matrícula** (opcional) | Campo aceita a nova matrícula |
| 12.4 | Clicar em **Salvar** | Sistema atualiza os dados |
| 12.5 | Edição bem-sucedida | Dados atualizados na lista. Mensagem de sucesso exibida |

### Regras de negócio

- O **CPF não pode ser alterado** (campo somente leitura na edição)
- O **Nome** é salvo em UPPERCASE
- A **Matrícula** é normalizada para 7 dígitos
- O campo `updatedAt` é atualizado automaticamente

### Validações de QA

- [ ] Formulário abre com dados atuais da pessoa preenchidos
- [ ] Campo CPF está desabilitado/somente leitura
- [ ] Nome em branco não permite salvar
- [ ] Dados são atualizados corretamente na lista após salvar
- [ ] Data de atualização (updatedAt) é atualizada

---

## Etapa 13 — Desativar / Ativar Pessoa

### Objetivo
Desativar temporariamente uma pessoa (sem excluir) ou reativar uma pessoa previamente desativada.

### Pré-condições
- Pessoa já cadastrada no sistema
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**

### Passo a passo — Desativar

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 13.1 | Na lista de Pessoas, clicar no **ícone de desativar** (🚫) | Exibe confirmação "Deseja desativar esta pessoa?" |
| 13.2 | Confirmar a desativação | Pessoa é marcada como **inativa** |
| 13.3 | Desativação bem-sucedida | Pessoa aparece como inativa na lista (visual diferenciado). Ícone muda para "Ativar" |

### Passo a passo — Reativar

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 13.4 | Na lista de Pessoas, localizar a pessoa inativa | Pessoa aparece com visual de inativa |
| 13.5 | Clicar no **ícone de ativar** | Exibe confirmação "Deseja reativar esta pessoa?" |
| 13.6 | Confirmar a reativação | Pessoa volta ao status **ativa** |

### Validações de QA

- [ ] Pessoa desativada não é excluída do sistema (apenas fica inativa)
- [ ] Pessoa inativa tem visual diferenciado na lista
- [ ] Dados da pessoa inativa ainda podem ser consultados
- [ ] Documentos da pessoa inativa ainda são acessíveis
- [ ] É possível reativar uma pessoa previamente desativada

---

## Etapa 14 — Excluir Pessoa

### Objetivo
Remover **definitivamente** uma pessoa e todos os seus dados vinculados.

### Pré-condições
- Pessoa já cadastrada no sistema
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 14.1 | Na lista de Pessoas, clicar no **ícone de exclusão** (🗑️ vermelho) | Exibe confirmação com aviso de irreversibilidade |
| 14.2 | Ler o aviso de que a ação é **irreversível** | Modal exibe: "Esta ação não pode ser desfeita. Todos os documentos e dados da pessoa serão removidos." |
| 14.3 | Confirmar a exclusão | Pessoa é removida definitivamente |
| 14.4 | Exclusão bem-sucedida | Pessoa desaparece da lista. Mensagem de sucesso exibida |

### O que é removido

- Cadastro da pessoa
- Todos os documentos vinculados
- Todas as entries (rubricas extraídas)
- Arquivos PDF armazenados (GridFS)
- Referências cruzadas

### Validações de QA

- [ ] Modal de confirmação é exibido antes de excluir
- [ ] Aviso de irreversibilidade está claro
- [ ] Após exclusão, pessoa não aparece mais na lista
- [ ] Documentos da pessoa são removidos
- [ ] Entries da pessoa são removidas
- [ ] Não é possível acessar a pessoa por CPF ou ID após exclusão

---

## Etapa 15 — Excluir Documento

### Objetivo
Remover um documento específico de uma pessoa, incluindo todas as entries extraídas dele.

### Pré-condições
- Documento já vinculado a uma pessoa
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 15.1 | Acessar a lista de documentos da pessoa | Lista de documentos visível |
| 15.2 | Localizar o documento a ser excluído | Documento visível na lista |
| 15.3 | Clicar no **ícone de exclusão** do documento | Exibe confirmação |
| 15.4 | Confirmar a exclusão | Documento é removido |
| 15.5 | Exclusão bem-sucedida | Documento desaparece da lista. Contador de documentos da pessoa é atualizado |

### O que é removido

- O documento (registro no banco)
- Todas as entries extraídas daquele documento
- O arquivo PDF armazenado (GridFS)
- A referência do documento na lista de documentos da pessoa

### Validações de QA

- [ ] Confirmação é exibida antes de excluir
- [ ] Apenas o documento selecionado é removido (outros permanecem)
- [ ] Entries daquele documento são removidas
- [ ] Contador de documentos da pessoa é atualizado
- [ ] Consolidação é recalculada (sem os dados do documento excluído)

---

## Etapa 16 — Reprocessar Documento

### Objetivo
Reprocessar um documento que já foi processado (para atualizar entries) ou que teve erro no processamento anterior.

### Pré-condições
- Documento com status **PROCESSED** ou **ERROR**
- Estar autenticado com role **TENANT_ADMIN** ou **SUPER_ADMIN**

### Passo a passo

| # | Ação | Resultado Esperado |
|---|------|--------------------|
| 16.1 | Acessar a lista de documentos da pessoa | Lista de documentos visível |
| 16.2 | Localizar um documento com status **PROCESSED** ou **ERROR** | Documento visível com status |
| 16.3 | Clicar no botão/ícone **Reprocessar** | Confirmação exibida |
| 16.4 | Confirmar o reprocessamento | Status muda para **PROCESSING** |
| 16.5 | Aguardar reprocessamento | Entries anteriores são substituídas pelas novas |
| 16.6 | Reprocessamento concluído | Status volta para **PROCESSED** com entries atualizadas |

### Regras de negócio

- Documentos com status **PENDING** **não podem** ser reprocessados (devem ser processados pela primeira vez)
- Documentos com status **PROCESSING** **não podem** ser reprocessados (já estão em processamento)
- O reprocessamento **substitui** todas as entries anteriores pelas novas

### Validações de QA

- [ ] Documento PROCESSED pode ser reprocessado
- [ ] Documento ERROR pode ser reprocessado
- [ ] Documento PENDING não pode ser reprocessado (botão desabilitado ou mensagem de erro)
- [ ] Entries anteriores são removidas e substituídas pelas novas
- [ ] Consolidação é atualizada com os novos dados

---

## Fluxo Completo — Resumo Visual

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           JORNADA DO USUÁRIO                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐                   │
│  │  LOGIN   │───→│ TELA PESSOAS │───→│ NOVA PESSOA  │                   │
│  │ (Etapa 1)│    │  (Etapa 2)   │    │  (Etapa 3)   │                   │
│  └──────────┘    └──────┬───────┘    └──────┬───────┘                   │
│                         │                   │                           │
│                         │     ┌─────────────┘                           │
│                         │     │                                         │
│                         ▼     ▼                                         │
│                  ┌──────────────┐                                       │
│                  │   UPLOAD     │                                       │
│                  │ DOCUMENTOS   │                                       │
│                  │  (Etapa 4)   │                                       │
│                  └──────┬───────┘                                       │
│                         │                                               │
│                         ▼                                               │
│                  ┌──────────────┐                                       │
│                  │ PROCESSAMENTO│                                       │
│                  │  (Etapa 5)   │                                       │
│                  └──────┬───────┘                                       │
│                         │                                               │
│              ┌──────────┼──────────┐                                    │
│              ▼          ▼          ▼                                    │
│       ┌────────┐ ┌───────────┐ ┌──────────┐                             │
│       │ VER    │ │CONSOLIDAR │ │ EXPORTAR │                             │
│       │ENTRIES │ │  MATRIZ   │ │  EXCEL   │                             │
│       │(Et. 7) │ │ (Etapa 8) │ │(Etapa 9) │                             │
│       └────────┘ └───────────┘ └──────────┘                             │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────┐            │
│  │ AÇÕES ADMINISTRATIVAS                                   │            │
│  │ Editar (Et.12) | Desativar (Et.13) | Excluir (Et.14)    │            │
│  │ Excluir Doc (Et.15) | Reprocessar (Et.16)               │            │
│  └─────────────────────────────────────────────────────────┘            │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Checklist Geral de QA

### Fluxo principal (caminho feliz)

- [ ] Login → Tela de Pessoas → Cadastrar Pessoa → Upload PDFs → Processar → Ver Entries → Consolidar → Exportar Excel
- [ ] Todo o fluxo funciona do início ao fim sem erros

### Segurança e permissões

- [ ] TENANT_USER pode visualizar, mas não pode criar/editar/excluir
- [ ] TENANT_ADMIN pode fazer todas as operações no seu tenant
- [ ] SUPER_ADMIN pode fazer todas as operações em todos os tenants
- [ ] Dados de um tenant não são visíveis para outros tenants

### Tratamento de erros

- [ ] Todos os erros exibem mensagens claras e em português
- [ ] Erros de rede exibem mensagem "Sem conexão com o servidor"
- [ ] Token expirado faz refresh automático sem perder a ação do usuário
- [ ] Operações destrutivas (excluir) pedem confirmação

### Responsividade e UX

- [ ] Indicadores de loading são exibidos durante operações demoradas
- [ ] Mensagens de sucesso são exibidas após cada ação
- [ ] Listas são atualizadas automaticamente após criar/editar/excluir
- [ ] Paginação funciona corretamente em listas grandes

---

> **Última atualização:** Fevereiro 2026
