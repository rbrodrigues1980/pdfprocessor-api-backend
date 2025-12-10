# 📝 Como os Dados da Person São Salvos

## 🔍 Resumo do Fluxo

### 1. **CPF (Campo: `cpf`)**
- **Normalização**: Remove todos os caracteres não numéricos
- **Validação**: Deve ter exatamente 11 dígitos
- **Como é salvo**: Apenas dígitos (ex: `"12449709568"`)
- **Exemplo**: 
  - Input: `"124.497.095-68"` ou `"12449709568"`
  - Output: `"12449709568"`

### 2. **Nome (Campo: `nome`)**
- **Normalização**: Converte para MAIÚSCULAS e remove espaços extras
- **Como é salvo**: Todo em MAIÚSCULAS (ex: `"FLAVIO JOSE PEREIRA ALMEIDA"`)
- **Exemplo**:
  - Input: `"Flavio Jose Pereira Almeida"` ou `"  flavio jose  "`
  - Output: `"FLAVIO JOSE PEREIRA ALMEIDA"`

### 3. **Matrícula (Campo: `matricula`)**
- **Normalização**: Remove todos os caracteres não numéricos
- **Validação**: Deve ter exatamente 7 dígitos
- **Como é salvo**: Apenas dígitos (ex: `"0437412"`)
- **Problema**: Se não tiver 7 dígitos após normalização, não é salvo (fica `null`)
- **Exemplo**:
  - Input: `"043741-2"` ou `"0437412"`
  - Output: `"0437412"`
  - Se tiver menos ou mais de 7 dígitos: `null` (não aparece no JSON do MongoDB)

## ⚠️ Problema Identificado

O MongoDB **não mostra campos null no JSON**. Se a matrícula for `null`, ela não aparecerá no documento.

## 🔧 Correções Necessárias

1. Garantir que a matrícula seja sempre salva quando fornecida (mesmo se a Person já existir)
2. Adicionar validação para aceitar matrículas com 7 dígitos
3. Garantir que a atualização ocorra tanto no `ensurePersonExists()` quanto quando o documento é salvo

