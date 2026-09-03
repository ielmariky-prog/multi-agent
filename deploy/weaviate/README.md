# Weaviate sur Kubernetes (namespace `weaviate`)

Ce guide explique comment installer Weaviate via Helm, l’exposer pour des tests et lancer des requêtes rapides.

---

## 1. Installation avec Helm

1. Ajouter le dépôt :
   ```bash
   helm repo add weaviate https://weaviate.github.io/weaviate-helm
   helm repo update
   ```

2. Créer le namespace dédié :
   ```bash
   kubectl create namespace weaviate
   ```

3. Installer avec des valeurs adaptées (fichier `values.yaml`) :
   ```yaml
   replicaCount: 1
   persistence:
     enabled: true
     size: 20Gi
   resources:
     requests:
       cpu: 500m
       memory: 1Gi
   service:
     type: ClusterIP
     port: 8080
   ```

4. Déployer :
   ```bash
   helm upgrade --install weaviate weaviate/weaviate \
     -n weaviate \
     -f values.yaml
   ```

---

## 2. Vérifications & exposition locale

```bash
kubectl get pods -n weaviate
kubectl get svc weaviate -n weaviate
```

Pour tester depuis ton poste :
```bash
kubectl port-forward svc/weaviate 8080:8080 -n weaviate
# puis : curl http://localhost:8080/v1/.well-known/ready
```

---

## 3. Requêtes rapides

- **GraphQL** :
  ```bash
  curl http://localhost:8080/v1/graphql \
    -H "Content-Type: application/json" \
    -d '{
      "query": "{
        Get {
          Expense(limit: 5) {
            description
            amount
            date
          }
        }
      }"
    }'
  ```

- **REST** :
  - Création d’un objet :
    ```bash
    curl -X POST http://localhost:8080/v1/objects \
      -H "Content-Type: application/json" \
      -d '{
        "class": "Expense",
        "properties": {
          "description": "Taxi Paris",
          "amount": 42,
          "date": "2024-03-10"
        }
      }'
    ```
  - Création d'une Facture (Invoice) :
    > **Note**: Assure-toi d'avoir créé la classe `Invoice` dans le schéma Weaviate au préalable.
    ```bash
    curl -X POST http://localhost:8080/v1/objects \
      -H "Content-Type: application/json" \
      -d '{
        "class": "Invoice",
        "properties": {
          "clientName": "ACME Corp",
          "invoiceNumber": "FACT-2026-001",
          "date": "2026-03-15T00:00:00Z",
          "dueDate": "2026-04-15T00:00:00Z",
          "totalAmount": 3800.00,
          "vatAmount": 760.00,
          "status": "DRAFT",
          "itemsJson": "[{\"description\":\"Développement back-end\",\"quantity\":5,\"unitPrice\":600.00},{\"description\":\"Gestion de projet\",\"quantity\":1,\"unitPrice\":800.00}]",
          "sourceText": "Facture ACME pour 5 jours de dev back-end et 1 jour de gestion de projet"
        }
      }'
    ```
  - Lecture : `GET /v1/objects/{class}/{uuid}`

---

## 4. Utilisation par les services

Dans les autres namespaces (ex. `multi-agent`), référence Weaviate via le DNS interne :
```
http://weaviate.weaviate.svc.cluster.local:8080
```

Configure cette URL dans les ConfigMaps/Secrets de tes microservices (`WEAVIATE_HOST`). Assure-toi aussi que les `NetworkPolicy` autorisent les flux `multi-agent -> weaviate`.
