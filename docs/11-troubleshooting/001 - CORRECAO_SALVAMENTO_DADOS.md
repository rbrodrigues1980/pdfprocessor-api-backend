# ✅ Correção: Salvamento de Nome, CPF e Matrícula

## 📋 Problema Identificado

Os dados da Person (nome, CPF e matrícula) devem ser salvos **APENAS durante o upload**, não durante o processamento do documento.

## 🔧 Correções Realizadas

### 1. **Remoção de Lógica Duplicada**
- ❌ **ANTES**: Havia lógica duplicada tentando atualizar nome/matrícula após salvar o documento
- ✅ **AGORA**: Nome, CPF e matrícula são salvos **apenas** no `ensurePersonExists()` durante o upload
- ✅ Após salvar o documento, apenas adicionamos o `documentoId` à lista de documentos da Person

### 2. **Fluxo Simplificado**

```
📤 UPLOAD (bulk-upload)
  ↓
  DocumentUploadUseCase.upload()
  ↓
  ensurePersonExists(cpf, nome, matricula)
  ↓
  ✅ Salva/Atualiza Person com:
     - CPF (normalizado: apenas dígitos)
     - Nome (normalizado: MAIÚSCULAS)
     - Matrícula (normalizado: 7 dígitos numéricos)
  ↓
  Salva PayrollDocument
  ↓
  Adiciona documentoId à lista de documentos da Person
  ↓
  ✅ Person salva com todos os dados

🔄 PROCESSAMENTO
  ↓
  DocumentProcessUseCase.processDocument()
  ↓
  ✅ NÃO mexe em Person (apenas processa PDF e extrai entries)
```

### 3. **Garantias Implementadas**

1. **CPF**: Sempre salvo quando fornecido (normalizado para 11 dígitos)
2. **Nome**: Sempre salvo quando fornecido (normalizado para MAIÚSCULAS)
3. **Matrícula**: Sempre salva quando fornecida e válida (7 dígitos numéricos)
4. **Sem Interferência**: O processamento do documento não altera dados da Person

## 📝 Comportamento Esperado

### Quando a Person JÁ EXISTE:
- ✅ Nome é atualizado se fornecido e diferente
- ✅ Matrícula é atualizada se fornecida e válida (7 dígitos)
- ✅ CPF não muda (é o identificador único)

### Quando a Person NÃO EXISTE:
- ✅ Person é criada com CPF, nome e matrícula fornecidos
- ✅ Todos os dados são normalizados antes de salvar

## 🎯 Resultado Final

Agora, quando você faz o upload com:
```bash
curl -X 'POST' \
  'http://localhost:8080/api/v1/documents/bulk-upload' \
  -F 'cpf=12449709568' \
  -F 'nome=FLAVIO JOSE PEREIRA ALMEIDA' \
  -F 'matricula=0437412' \
  -F 'files=@...'
```

A Person será criada/atualizada **imediatamente** durante o upload com:
- ✅ `cpf`: "12449709568"
- ✅ `nome`: "FLAVIO JOSE PEREIRA ALMEIDA"
- ✅ `matricula`: "0437412"

E **não será alterada** durante o processamento do documento.

