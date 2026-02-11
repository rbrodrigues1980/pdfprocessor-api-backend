# Instalação rápida para testar no seu notebook

Guia para subir a **PDF Processor API** no seu computador de forma simples, usando Docker. A API usa o **MongoDB já configurado na aplicação** (ex.: MongoDB Atlas) — não sobe banco local.

---

## O que você precisa

1. **Docker Desktop** (Windows)
   - Baixe e instale: https://www.docker.com/products/docker-desktop/
   - Depois de instalar, **inicie o Docker Desktop** (ícone na bandeja do sistema).

2. **Pasta do projeto**
   - Receba a pasta do projeto (por exemplo, `pdfprocessor-api-backend`) e coloque onde quiser (ex.: `C:\dev\pdfprocessor-api-backend` ou na Área de trabalho).

3. **Arquivo `.env`** — use as mesmas credenciais que quem desenvolve usa localmente
   - **Recomendado:** peça ao dono do projeto o arquivo **`.env`** que ele usa na raiz do projeto ao rodar localmente. Coloque esse `.env` na pasta do projeto no seu PC. Assim a API no Docker usa o mesmo MongoDB e JWT que os testes locais (sem precisar preencher nada).
   - Alternativa: copie `.env.example` para `.env` e preencha **MONGODB_URI** e **JWT_SECRET** com os mesmos valores que o desenvolvedor usa localmente.

---

## Subir a aplicação (toda vez que ligar o PC)

1. **Inicie o Docker Desktop** (se ainda não estiver aberto).
2. Na pasta do projeto, dê **dois cliques** em:
   - **`INICIAR.bat`**
3. Aguarde a mensagem de sucesso (na primeira vez pode levar 1–2 minutos para baixar imagens e construir a API).
4. Abra no navegador:
   - **API / Swagger:** http://localhost:8081/swagger-ui.html  
   - **Health:** http://localhost:8081/actuator/health  

A aplicação fica rodando em segundo plano. Você pode fechar a janela do script; ela continua ativa.

---

## Parar a aplicação (quando for desligar ou não for usar)

1. Na pasta do projeto, dê **dois cliques** em:
   - **`PARAR.bat`**
2. Pronto. A API para (o MongoDB continua na nuvem, se for Atlas).

---

## Resumo do dia a dia

| Ação              | O que fazer                          |
|-------------------|--------------------------------------|
| Ligar o PC        | Abrir Docker Desktop (se quiser usar a API). |
| Quero usar a API  | Dois cliques em **INICIAR.bat**      |
| Testar no browser | Abrir http://localhost:8081/swagger-ui.html |
| Parar tudo        | Dois cliques em **PARAR.bat**        |
| Desligar o PC     | Pode desligar; se esqueceu, rode **PARAR.bat** antes na próxima vez. |

---

## Problemas comuns

- **“Docker não está rodando”**  
  Inicie o **Docker Desktop** e espere o ícone indicar que está ativo. Depois rode **INICIAR.bat** de novo.

- **Porta 8081 já em uso**  
  Algum outro programa está usando a porta 8081. Feche esse programa ou altere a porta no `docker-compose.yml` (e no script, se necessário).

- **“Failed looking up SRV record for '_mongodb._tcp.cluster.mongodb.net'” ou “DNS name not found”**  
  O `.env` está com a URI do MongoDB **placeholder** (`cluster.mongodb.net`), que não existe. É preciso usar a **URI real** do MongoDB Atlas (a mesma que funciona na sua máquina). No `.env`, em `MONGODB_URI`, substitua por uma URI completa, por exemplo:  
  `mongodb+srv://usuario:senha@NOMEDOCLUSTER.xxxxx.mongodb.net/pdfprocessor?retryWrites=true&w=majority`  
  O trecho `NOMEDOCLUSTER.xxxxx.mongodb.net` vem do Atlas (Cluster → Connect → Drivers). Não deixe `cluster.mongodb.net` no lugar.

- **Primeira vez muito lenta**  
  Normal. O Docker constrói a imagem da API. Nas próximas vezes fica bem mais rápido.

- **Quero ver os logs da API**  
  No terminal (PowerShell ou CMD), na pasta do projeto:
  ```bash
  docker-compose logs -f api
  ```
  Para sair: `Ctrl+C`.

---

## Dados

- Os dados ficam no **MongoDB já configurado** (ex.: Atlas). Parar ou reiniciar a API não apaga nada.

Se precisar de ajuda, envie a mensagem de erro que aparecer na tela ao rodar **INICIAR.bat** ou **PARAR.bat**.
