# ============================================
# Kubernetes Deployment - PDF Processor API
# ============================================

Este diretório contém os manifests Kubernetes para deploy da aplicação.

## Arquivos

| Arquivo | Descrição |
|---------|-----------|
| `namespace.yaml` | Namespace isolado para a aplicação |
| `secret.yaml` | Credenciais sensíveis (MongoDB, JWT) |
| `configmap.yaml` | Configurações não-sensíveis |
| `deployment.yaml` | Deployment com 2 réplicas e health checks |
| `service.yaml` | Service ClusterIP para comunicação interna |
| `ingress.yaml` | Ingress para exposição externa com domínio |
| `hpa.yaml` | HorizontalPodAutoscaler para auto-scaling |

## Pré-requisitos

1. Cluster Kubernetes (EKS, GKE, AKS, ou local com minikube/kind)
2. `kubectl` configurado
3. Imagem Docker no registry (Docker Hub, ECR, GCR, etc.)

## Deploy

### 1. Criar imagem e enviar para registry

```bash
# Build da imagem
docker build -t your-registry/pdfprocessor-api:v1.0.0 .

# Push para registry
docker push your-registry/pdfprocessor-api:v1.0.0
```

### 2. Atualizar configurações

Edite os arquivos antes de aplicar:

- `secret.yaml` → Substitua as credenciais reais
- `deployment.yaml` → Atualize a imagem para `your-registry/pdfprocessor-api:v1.0.0`
- `ingress.yaml` → Substitua `api.pdfprocessor.com` pelo seu domínio

### 3. Aplicar manifests

```bash
# Modo completo (todos os arquivos)
kubectl apply -f k8s/

# Ou arquivo por arquivo (em ordem)
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/hpa.yaml
```

### 4. Verificar status

```bash
# Ver pods
kubectl get pods -n pdfprocessor

# Ver logs
kubectl logs -f deployment/pdfprocessor-api -n pdfprocessor

# Ver serviços
kubectl get svc -n pdfprocessor

# Ver ingress
kubectl get ingress -n pdfprocessor

# Ver HPA
kubectl get hpa -n pdfprocessor
```

## Arquitetura no Kubernetes

```
┌─────────────────────────────────────────────────────────────┐
│                    INTERNET                                  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    INGRESS                                   │
│              (api.pdfprocessor.com)                         │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    SERVICE                                   │
│              (ClusterIP :80 → :8081)                        │
└───────────────────────┬─────────────────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
        ▼                               ▼
┌───────────────┐               ┌───────────────┐
│    POD 1      │               │    POD 2      │
│ pdfprocessor  │               │ pdfprocessor  │
│    :8081      │               │    :8081      │
└───────────────┘               └───────────────┘
        │                               │
        └───────────────┬───────────────┘
                        │
                        ▼
                ┌───────────────┐
                │  MongoDB Atlas │
                │   (externo)    │
                └───────────────┘
```

## Recursos do Cluster

| Componente | CPU Request | CPU Limit | Memory Request | Memory Limit |
|------------|-------------|-----------|----------------|--------------|
| API Pod    | 250m        | 1000m     | 512Mi          | 1Gi          |

Com 2-10 pods (HPA), o cluster precisa:
- **Mínimo**: 500m CPU, 1Gi RAM
- **Máximo**: 10 CPU, 10Gi RAM

## Troubleshooting

### Pod não inicia
```bash
kubectl describe pod <pod-name> -n pdfprocessor
kubectl logs <pod-name> -n pdfprocessor --previous
```

### Conexão com MongoDB falha
```bash
# Verificar secret
kubectl get secret pdfprocessor-secrets -n pdfprocessor -o yaml

# Verificar variáveis no pod
kubectl exec -it <pod-name> -n pdfprocessor -- env | grep MONGO
```

### HPA não escala
```bash
# Verificar métricas
kubectl top pods -n pdfprocessor
kubectl describe hpa pdfprocessor-api-hpa -n pdfprocessor
```
