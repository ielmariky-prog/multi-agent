# Netoyage node kube
```bash
docker system df
docker system prune -a --volumes
```

# 🧠 Multi-Agent Kafka + Weaviate + OpenAI (Kubernetes)


Déploie une architecture multi-agents IA complète sur Kubernetes (Docker Desktop compatible).

## 🚀 Composants
- **Kafka (Confluent Platform)** : Bus d’événements
- **Weaviate** : Base vectorielle pour mémoire sémantique
- **InputAgent** : Nettoie les messages
- **WeaviateSyncAgent** : Indexe dans Weaviate
- **ReasoningAgent** : Analyse avec OpenAI / LangChain4j
- **AuditAgent** : Surveille les échanges

## ⚙️ Déploiement
1. Namespace :
   ```bash
   kubectl apply -f k8s/namespace.yaml
   ```
2. Secret OpenAI :
   ```bash
   kubectl apply -f k8s/secrets/openai-secret.yaml
   ```
3. Kafka via Helm :
   ```bash
   helm repo add confluentinc https://packages.confluent.io/helm
   helm repo update
   helm install confluent-platform confluentinc/confluent-for-kubernetes -n agent-system -f k8s/kafka/values-kraft.yaml
   ```
4. Weaviate + Agents :
   ```bash
   kubectl apply -f k8s/weaviate/
   kubectl apply -f k8s/agents/
   ```

## 💾 Persistence
- Kafka : `/Users/<ton_user>/kafka-data`
- Weaviate : `/Users/<ton_user>/weaviate-data`

## 🔑 Secret OpenAI
Fichier :
```yaml
stringData:
  api-key: "sk-xxxx"
```

Crée le secret :
```bash
kubectl apply -f k8s/secrets/openai-secret.yaml
```
