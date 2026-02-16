# 🔍 Troubleshooting: Erros de Conexão MongoDB

## 📋 O Problema

Ao reiniciar a aplicação, você pode ver logs como:

```
WARN - Exception thrown during connection pool background maintenance task
com.mongodb.MongoSecurityException: Exception authenticating
Caused by: com.mongodb.MongoSocketReadTimeoutException: Timeout while receiving message
Caused by: io.netty.handler.timeout.ReadTimeoutException
```

## 🔍 Análise do Problema

### O que está acontecendo?

1. **Erro não é crítico**: A aplicação continua funcionando normalmente
2. **Tarefa de manutenção**: O erro ocorre durante a **manutenção em background do pool de conexões**
3. **Reconexão automática**: Após alguns segundos, a conexão é restabelecida com sucesso
4. **Pool de conexões**: O MongoDB driver tenta revalidar conexões antigas no pool, e algumas podem estar expiradas ou inativas

### Por que isso acontece?

- **Timeouts curtos**: Os timeouts de 30 segundos podem ser insuficientes em redes com alta latência
- **Manutenção do pool**: O driver MongoDB executa tarefas de manutenção periódicas para validar conexões
- **Latência de rede**: Dependendo da sua conexão com o MongoDB Atlas, pode haver atrasos temporários
- **Conexões inativas**: Conexões que ficaram inativas podem expirar antes de serem revalidadas

## ✅ Soluções Aplicadas

### 1. **Aumento dos Timeouts**

**Antes:**
- `socketTimeoutMS=30000` (30 segundos)
- `connectTimeoutMS=30000` (30 segundos)
- `serverSelectionTimeoutMS=30000` (30 segundos)

**Depois:**
- `socketTimeoutMS=60000` (60 segundos) ⬆️
- `connectTimeoutMS=60000` (60 segundos) ⬆️
- `serverSelectionTimeoutMS=60000` (60 segundos) ⬆️

**Benefício**: Mais tempo para estabelecer conexões, reduzindo timeouts durante latências de rede.

### 2. **Aumento do Tempo de Vida das Conexões Inativas**

**Antes:**
- `maxIdleTimeMS=60000` (1 minuto)

**Depois:**
- `maxIdleTimeMS=600000` (10 minutos) ⬆️

**Benefício**: Conexões ficam ativas por mais tempo, reduzindo a necessidade de reconexões frequentes.

### 3. **Aumento do Intervalo de Heartbeat**

**Antes:**
- `heartbeatFrequencyMS=10000` (10 segundos)

**Depois:**
- `heartbeatFrequencyMS=15000` (15 segundos) ⬆️

**Benefício**: Menos frequência de verificações de conexão, reduzindo sobrecarga e possíveis timeouts.

### 4. **Adição de Delays de Retry**

**Novo parâmetro:**
- `serverSelectionRetryDelayMS=5000` (5 segundos)

**Benefício**: Delay entre tentativas de seleção de servidor, dando tempo para a rede se estabilizar.

### 5. **Habilitação de Retry Reads**

**Novo parâmetro:**
- `retryReads=true`

**Benefício**: Tentativas automáticas de retry em leituras que falharem, aumentando a resiliência.

## 📊 Parâmetros da URI MongoDB Explicados

| Parâmetro | Valor | Descrição |
|-----------|-------|-----------|
| `serverSelectionTimeoutMS` | 60000 | Tempo máximo para selecionar um servidor (60s) |
| `connectTimeoutMS` | 60000 | Tempo máximo para estabelecer conexão (60s) |
| `socketTimeoutMS` | 60000 | Tempo máximo de inatividade antes de fechar socket (60s) |
| `maxPoolSize` | 50 | Número máximo de conexões no pool |
| `minPoolSize` | 5 | Número mínimo de conexões no pool |
| `maxIdleTimeMS` | 600000 | Tempo que uma conexão pode ficar inativa (10 min) |
| `heartbeatFrequencyMS` | 15000 | Frequência de verificação de saúde do servidor (15s) |
| `serverSelectionRetryDelayMS` | 5000 | Delay entre tentativas de seleção de servidor (5s) |
| `retryWrites` | true | Retry automático em operações de escrita |
| `retryReads` | true | Retry automático em operações de leitura |

## 🎯 Resultado Esperado

Após essas mudanças:

1. ✅ **Menos warnings**: Erros durante manutenção do pool devem ser raros
2. ✅ **Maior resiliência**: A aplicação tolera melhor problemas temporários de rede
3. ✅ **Reconexão mais suave**: Conexões são mantidas por mais tempo, reduzindo reconexões
4. ✅ **Melhor performance**: Menos overhead de reconexões frequentes

## 🚨 Quando se Preocupar?

O erro é **NORMAL** e **NÃO CRÍTICO** se:

- ✅ A aplicação continua funcionando após o erro
- ✅ A conexão é restabelecida automaticamente
- ✅ Os erros ocorrem apenas esporadicamente (não constantemente)
- ✅ Os logs mostram reconexão bem-sucedida logo após

**Preocupe-se se:**

- ❌ A aplicação não consegue conectar ao MongoDB
- ❌ Os erros são constantes e não há reconexão
- ❌ Operações de banco estão falhando
- ❌ A aplicação não inicia corretamente

## 📝 Monitoramento

Para monitorar a saúde das conexões, observe os logs:

**✅ Sinais de saúde:**
```
INFO - Monitor thread successfully connected to server
INFO - Discovered replica set primary
```

**⚠️ Sinais de problema (mas normalmente se resolvem):**
```
WARN - Exception thrown during connection pool background maintenance task
```

**❌ Sinais críticos:**
```
ERROR - Failed to connect to server
ERROR - No servers available
```

## 🔄 Próximos Passos (Opcional)

Se os warnings ainda persistirem após essas mudanças:

1. **Verificar rede**: Testar conectividade com o MongoDB Atlas
2. **Firewall**: Verificar se não há bloqueios de firewall
3. **Região do cluster**: Verificar se o cluster está em uma região próxima
4. **Atualizar driver**: Considerar atualizar a versão do driver MongoDB
5. **Monitoramento**: Implementar métricas de conexão (Micrometer/Prometheus)

---

**Data da atualização**: 2025-11-30  
**Status**: ✅ Configurações otimizadas aplicadas

