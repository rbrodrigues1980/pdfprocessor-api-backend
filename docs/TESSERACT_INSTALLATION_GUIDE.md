# 🔧 Guia de Instalação do Tesseract (Windows)

> [!IMPORTANT]
> Este guia é obrigatório para usar a funcionalidade de extração de texto de imagens. O Tesseract precisa estar instalado no sistema operacional.

---

## 📥 Passo 1: Baixar o Instalador

1. Acesse a página oficial do Tesseract para Windows:
   
   **🔗 https://github.com/UB-Mannheim/tesseract/wiki**

2. Na seção **"Tesseract at UB Mannheim"**, clique no link da versão mais recente:
   - Arquivo: `tesseract-w64-setup-5.3.3.XXXXXXXX.exe` (ou versão mais recente)
   - Tamanho: ~50-80 MB

3. Salve o arquivo no seu computador

---

## 💿 Passo 2: Executar o Instalador

1. **Execute o instalador** como Administrador (clique com botão direito → "Executar como administrador")

2. **Tela de Boas-vindas**:
   - Clique em **"Next"**

3. **Aceitar Licença**:
   - Marque "I accept the terms in the License Agreement"
   - Clique em **"Next"**

4. **⚠️ IMPORTANTE - Selecionar Componentes**:
   
   Certifique-se de que os seguintes itens estejam **marcados**:
   
   ```
   ✅ Tesseract - Core
   ✅ Additional language data (download)
      ✅ Portuguese
      ✅ Portuguese (Brazil) - se disponível
   ✅ Development tools
   ```

   > [!CAUTION]
   > Se você esquecer de marcar "Portuguese", a extração de texto não funcionará para português!

5. **Local de Instalação**:
   - Caminho padrão (recomendado): `C:\Program Files\Tesseract-OCR` (nome oficial da pasta de instalação)
   - ✅ Anote este caminho, será usado na configuração
   - Clique em **"Install"**

6. **Aguarde a instalação** (pode levar alguns minutos)

7. **Concluir**:
   - Clique em **"Finish"**

---

## ✅ Passo 3: Verificar a Instalação

Abra o **PowerShell** e execute:

```powershell
& "C:\Program Files\Tesseract-OCR\tesseract.exe" --version
```

**Resultado esperado:**
```
tesseract 5.3.3
 leptonica-1.83.1
  libgif 5.2.1 : libjpeg 8d (libjpeg-turbo 2.1.5.1) : libpng 1.6.40 : libtiff 4.5.1 : zlib 1.2.13 : libwebp 1.3.2 : libopenjp2 2.5.0
 Found AVX2
 Found AVX
 Found FMA
 Found SSE4.1
 Found libarchive 3.6.2 zlib/1.2.13 liblzma/5.4.1 bz2lib/1.0.8 liblz4/1.9.4 libzstd/1.5.4
```

Se você vir uma mensagem de erro como `"O termo 'tesseract.exe' não é reconhecido..."`, a instalação pode ter falhado.

---

## 📦 Passo 4: Verificar Dados de Treinamento em Português

Execute:

```powershell
dir "C:\Program Files\Tesseract-OCR\tessdata\por.*"
```

**Resultado esperado:**
```
por.traineddata
```

Se o arquivo `por.traineddata` **NÃO** estiver presente:

### Download Manual dos Dados de Treinamento

1. Acesse: https://github.com/tesseract-ocr/tessdata

2. Baixe o arquivo: **`por.traineddata`**
   - Clique em `por.traineddata`
   - Clique no botão **"Download"** (lado direito)

3. Copie o arquivo baixado para:
   ```
   C:\Program Files\Tesseract-OCR\tessdata\
   ```

4. Verifique novamente com o comando acima

---

## ⚙️ Passo 5: Verificar Configuração da Aplicação

O arquivo `application.yml` já está configurado com o caminho padrão:

**Arquivo**: [`application.yml`](file:///d:/dev/projects/pdfprocessor-api-backend/src/main/resources/application.yml)

```yaml
text-extraction:
  tesseract:
    datapath: "C:/Program Files/Tesseract-OCR/tessdata"
    language: "por"
    dpi: 300
```

> [!NOTE]
> Se você instalou o Tesseract em um local diferente, atualize o `datapath` no arquivo `application.yml`

---

## 🧪 Passo 6: Testar a Extração de Texto

1. **Inicie a aplicação**:
   ```powershell
   cd d:\dev\projects\pdfprocessor-api-backend
   .\gradlew.bat bootRun
   ```

2. **Acesse o Swagger**:
   - URL: http://localhost:8081/swagger-ui.html

3. **Faça login**:
   - Use o endpoint `/api/v1/auth/login`

4. **Teste a extração de texto**:
   - Procure pela tag **"Text Extraction"**
   - Teste o endpoint `/api/v1/text-extraction/detect`
   - Faça upload do demonstrativo de pagamento

**Se funcionar**, você verá:
```json
{
  "filename": "demonstrativo_pagamento.pdf",
  "isImageBased": true,
  "requiresTextExtraction": true,
  "recommendation": "This PDF appears to be image-based. Use text extraction endpoints to extract text."
}
```

---

## 🐛 Troubleshooting

### Erro: "Tesseract is not installed or not in PATH"

**Causa**: O Windows não encontrou o executável do Tesseract.

**Solução 1 - Adicionar ao PATH** (recomendado):

1. Pressione `Win + R`, digite `sysdm.cpl` e pressione Enter
2. Na aba **"Avançado"**, clique em **"Variáveis de Ambiente"**
3. Em **"Variáveis do sistema"**, localize a variável **"Path"**
4. Clique em **"Editar"**
5. Clique em **"Novo"** e adicione:
   ```
   C:\Program Files\Tesseract-OCR
   ```
6. Clique em **"OK"** em todas as janelas
7. **Reinicie o PowerShell** e a aplicação

**Solução 2 - Configurar caminho completo no código**:

Se não quiser mexer no PATH, você pode especificar o caminho completo na configuração (já feito no `application.yml`).

---

### Erro: "Error opening data file ... por.traineddata"

**Causa**: Arquivo de dados de treinamento em português não encontrado.

**Solução**:

1. Baixe manualmente: https://github.com/tesseract-ocr/tessdata/raw/main/por.traineddata
2. Copie para: `C:\Program Files\Tesseract-OCR\tessdata\`
3. Verifique com:
   ```powershell
   dir "C:\Program Files\Tesseract-OCR\tessdata\por.traineddata"
   ```

---

### Erro: "Access Denied" ao copiar arquivo para tessdata

**Causa**: O diretório `Program Files` requer permissões de administrador.

**Solução**:

1. Abra o **Explorador de Arquivos** como Administrador:
   - Pressione `Win + X`
   - Escolha "Windows PowerShell (Admin)"
   - Digite: `explorer.exe`
2. Navegue até `C:\Program Files\Tesseract-OCR\tessdata\`
3. Cole o arquivo `por.traineddata`

---

## 📚 Recursos Adicionais

- **Documentação Oficial**: https://tesseract-ocr.github.io/ (URL oficial do projeto)
- **GitHub Tesseract**: https://github.com/tesseract-ocr/tesseract (repositório oficial)
- **Dados de Treinamento**: https://github.com/tesseract-ocr/tessdata (repositório oficial)
- **Tess4J (Java wrapper)**: http://tess4j.sourceforge.net/

---

## ✅ Checklist Final

Antes de usar a extração de texto, certifique-se de que:

- [x] Tesseract foi instalado em `C:\Program Files\Tesseract-OCR`
- [x] Dados de treinamento em português (`por.traineddata`) estão instalados
- [x] Comando `tesseract --version` funciona no PowerShell
- [x] Arquivo `application.yml` tem o `datapath` correto
- [x] Aplicação inicia sem erros relacionados ao Tesseract

---

## 🎉 Parabéns!

Se todos os passos foram concluídos, você está pronto para usar extração de texto no seu sistema de processamento de PDFs!

**Próximo passo**: Teste com o demonstrativo de pagamento da Caixa usando o endpoint `/api/v1/text-extraction/extract-text` 🚀
