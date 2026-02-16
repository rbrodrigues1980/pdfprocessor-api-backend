# 09 — Deploy

> Guias de deploy e configuração de infraestrutura em produção.

---

## Documentos nesta seção

| Documento | Descrição |
|-----------|-----------|
| [001 - DEPLOY_GOOGLE_CLOUD_RUN.md](./001%20-%20DEPLOY_GOOGLE_CLOUD_RUN.md) | **Guia completo de deploy no Google Cloud Run (~648 linhas).** Cobre: alterações no código (application.yml, Dockerfile, SecurityConfig), configuração no Cloud Console, CI/CD com GitHub, otimização de build com Kaniko cache, troubleshooting, estratégia de logging e checklists de verificação. |

---

## Checklist rápido de deploy

1. Configurar variáveis de ambiente (MongoDB URI, Gemini API Key, JWT Secret)
2. Verificar Dockerfile e build do projeto
3. Configurar Cloud Run no Google Cloud Console
4. Configurar CI/CD com GitHub Actions
5. Validar logs e health check após deploy

---

[← Voltar ao índice](../README.md)
