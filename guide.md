# Guide de déploiement multi-agent

Ce document décrit pas à pas la mise en service de la plateforme multi‑agents (AI‑Core, Intent, Reasoning, Reassign, Audit, etc.) autour de Kafka et Weaviate.

---

## 1. Pré-requis

- **Outils** : Docker (ou Podman) + Compose, Java 21, Maven.
- **Accès** : clé OpenAI valide, cluster Kafka, instance Weaviate (>= 1.25).
- **Variables sensibles** (à exporter dans ton shell ou fichier `.env`) :

```bash
export OPENAI_API_KEY=sk-...
export WEAVIATE_HOST=http://weaviate:8085
export KAFKA_BOOTSTRAP_SERVERS=kafka:9092
export INTENT_INPUT_TOPIC=intent-input-topic
export INTENT_OUTPUT_TOPIC=reasoning-input-topic
export REASONING_INPUT_TOPIC=reasoning-input-topic
export REASONING_OUTPUT_TOPIC=reassign-input-topic
export REASSIGN_OUTPUT_TOPIC=audit.events.in
export AUDIT_INPUT_TOPIC=audit.events.in
export AUDIT_OUTPUT_TOPIC=audit.events.out
# Ajout pour la génération de factures
export WEAVIATE_INVOICE_CLASS=Invoice
```

Adapte les noms de topics/group IDs selon ton contexte.

---

## 2. Compilation locale (offline-friendly)

Si l’environnement bloque l’écriture dans `~/.m2`, copie le cache Maven dans `.m2-work` :

```bash
mkdir -p .m2-work/repository
cp -R ~/.m2/repository .m2-work/
```

Puis compile chaque module en utilisant ce dépôt local :

```bash
for module in ai-core intent-agent reasoning-agent reassign-agent audit-agent llm-gateway weaviate-sync-agent; do
  (cd $module && mvn -q -e -Dmaven.repo.local=../.m2-work/repository clean package)
done
```

Les JARs sont disponibles sous `target/`.

---

## 3. Construction des images Docker

Chaque module possède un `Dockerfile`. Construis et, si besoin, pousse les images :

```bash
docker build -t dokeryelmariki/ai-core:latest ai-core
docker build -t dokeryelmariki/intent-agent:latest intent-agent
docker build -t dokeryelmariki/reasoning-agent:latest reasoning-agent
docker build -t dokeryelmariki/reassign-agent:latest reassign-agent
docker build -t dokeryelmariki/audit-agent:latest audit-agent
docker build -t dokeryelmariki/llm-gateway:latest llm-gateway
docker build -t dokeryelmariki/weaviate-sync-agent:latest weaviate-sync-agent

docker push dokeryelmariki/ai-core:latest
...
```

---

## 4. Provisionner les dépendances

1. **Kafka**  
   - Déploie ton cluster (Helm, Compose, service managé…).  
   - Crée les topics nécessaires (`intent-input`, `reasoning-input`, `reassign-input`, `audit.events.*`, etc.).

2. **Weaviate**  
   - Lance Weaviate avec un backend vecteur (par ex. `docker compose -f deploy/weaviate.yml up -d`).  
   - Note l’hôte/port à reporter dans `WEAVIATE_HOST`.

3. **OpenAI**  
   - Vérifie ton quota et les modèles utilisés (`openai.model`, `openai.embedding-model`, etc.).  
   - Prépare la variable `OPENAI_API_KEY`.

### Création des topics Kafka

Depuis un hôte équipé des CLI Kafka :

```bash
kafka-topics \
  --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
  --create \
  --topic documents.raw \
  --partitions 3 \
  --replication-factor 1
```

En environnement Kubernetes, tu peux exécuter directement la commande dans un pod Kafka :

```bash
kubectl exec -n agent-system kafka-0 -- \
  kafka-topics --create \
    --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
    --topic documents.raw \
    --partitions 3 \
    --replication-factor 1

kubectl exec -n agent-system kafka-0 -- \
  kafka-topics --describe \
    --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
    --topic documents.raw
```

Adapte le nom du pod (`kafka-0`), le namespace et les paramètres de partitions/réplication à ton cluster.

---

## 5. Configuration et déploiement des microservices

### Exemple docker-compose

```yaml
version: "3.9"
services:
  kafka:
    image: confluentinc/cp-kafka:7.6.1
    ...

  weaviate:
    image: semitechnologies/weaviate:1.25.7
    environment:
      QUERY_DEFAULTS_LIMIT: "20"
      ...

  ai-core:
    image: dokeryelmariki/ai-core:latest
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      WEAVIATE_HOST: weaviate:8085
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      WEAVIATE_EXPENSE_CLASS: Expense
      AI_CORE_INVOICES_STORAGE_PATH: /data/invoices
      INTENT_INPUT_TOPIC: ${INTENT_INPUT_TOPIC}
      ...
    depends_on: [kafka, weaviate]
    ports:
      - "8081:8081"

  intent-agent:
    image: dokeryelmariki/intent-agent:latest
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      AI_CORE_URL: http://ai-core:8081
      INTENT_INPUT_TOPIC: ${INTENT_INPUT_TOPIC}
      INTENT_OUTPUT_TOPIC: ${INTENT_OUTPUT_TOPIC}
      AI_CORE_TIMEOUT: 8s
    depends_on: [ai-core, kafka]

  reasoning-agent:
    image: dokeryelmariki/reasoning-agent:latest
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      AI_CORE_URL: http://ai-core:8081
      REASONING_INPUT_TOPIC: ${REASONING_INPUT_TOPIC}
      REASONING_OUTPUT_TOPIC: ${REASONING_OUTPUT_TOPIC}
      AI_CORE_TIMEOUT: 8s
    depends_on: [ai-core, kafka]

  reassign-agent:
    image: dokeryelmariki/reassign-agent:latest
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      AI_CORE_URL: http://ai-core:8081
      REASSIGN_OUTPUT_TOPIC: ${REASSIGN_OUTPUT_TOPIC}
    depends_on: [reasoning-agent]

  audit-agent:
    image: dokeryelmariki/audit-agent:latest
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      AUDIT_INPUT_TOPIC: ${AUDIT_INPUT_TOPIC}
      AUDIT_OUTPUT_TOPIC: ${AUDIT_OUTPUT_TOPIC}
    depends_on: [reassign-agent]
```

Adapte selon ton orchestrateur (Kubernetes, Nomad, etc.) avec les mêmes variables.

---
docker build -t dokeryelmariki/ai-core:1.3.0 ai-core
docker tag dokeryelmariki/ai-core:1.3.0 dokeryelmariki/ai-core:latest
docker push dokeryelmariki/ai-core:latest

kubectl create ns multi-agent
cd ai-core/deploy/k8s
kubectl apply -f secret.yaml 
kubectl apply -f configMap.yaml
kubectl apply -f ai-core-deploy.yaml
## 6. Démarrage & contrôles

1. Lancer la stack : `docker compose up -d`.
2. Vérifier la santé :
   - `curl http://localhost:8081/actuator/health` (AI-Core)
   - `curl http://<agent-host>:<port>/actuator/health`
3. Surveiller les logs : `docker logs -f intent-agent`, etc.  
   Confirme que chaque agent se connecte à Kafka et Weaviate sans erreur.

### Factures : profil société émettrice (`SellerProfile`)

Le PDF et l'Excel des factures récupèrent les coordonnées de la société émettrice depuis Weaviate, dans la classe `SellerProfile`.

Le format courant est :
- `companyName`
- `address`
- `rcs`
- `iban`
- `bic`
- `email`
- `capital`

Après un changement de code ou de schéma, redémarre `ai-core` pour laisser l'application créer ou compléter la classe :

```bash
kubectl -n multi-agent rollout restart deployment/ai-core
kubectl -n multi-agent rollout status deployment/ai-core
```

Vérifie ensuite que la classe `SellerProfile` est présente :

```bash
curl -s http://localhost:8099/v1/schema
```

### Créer le profil `IA-INSIGHT`

```bash
curl -X POST http://localhost:8099/v1/objects \
  -H 'Content-Type: application/json' \
  -d '{
    "class":"SellerProfile",
    "properties":{
      "companyName":"IA-INSIGHT",
      "address":"33, rue de la terrasse, 94260 Fresnes",
      "rcs":"RCS 987 654 321",
      "iban":"FR76 2823 3000 0194 2300 9442 604",
      "bic":"REVOFRP2",
      "email":"ia.insightsv@gmail.com",
      "capital":"1000 euros"
    }
  }'
```

### Vérifier le profil stocké

```bash
curl -X GET 'http://localhost:8099/v1/objects?class=SellerProfile&limit=20'
```

### Remplacer un profil existant

1. Liste les objets et repère l'`id` du profil `IA-INSIGHT` :

```bash
curl -X GET 'http://localhost:8099/v1/objects?class=SellerProfile&limit=20'
```

2. Supprime l'objet existant :

```bash
curl -X DELETE http://localhost:8099/v1/objects/<SELLER_PROFILE_ID>
```

3. Recrée le profil avec les nouvelles coordonnées `IBAN/BIC` via le `POST /v1/objects` ci-dessus.

### Générer le PDF ou l'Excel depuis les factures indexées

PDF :

```bash
curl --fail-with-body -X POST http://localhost:8081/invoices/pdf \
  -H 'Content-Type: application/json' \
  -o F-202604-01.pdf \
  -d '{
    "billingMonth":"2026-03",
    "sellerCompanyName":"IA-INSIGHT",
    "invoiceName":"F-202604-01"
  }'
```

Excel :

```bash
curl --fail-with-body -X POST http://localhost:8081/invoices/excel \
  -H 'Content-Type: application/json' \
  -o F-202604-01.xlsx \
  -d '{
    "billingMonth":"2026-03",
    "sellerCompanyName":"IA-INSIGHT",
    "invoiceName":"F-202604-01"
  }'
```

Si `curl` renvoie une erreur `500`, n'enregistre pas directement sous `.pdf` ou `.xlsx` sans `--fail-with-body`, sinon tu risques de sauvegarder un JSON d'erreur à la place du vrai fichier.

---

## 7. Test de bout en bout

1. Publie un texte dans le topic d’entrée :
   ```bash
   kafka-console-producer --broker-list kafka:9092 --topic ${INTENT_INPUT_TOPIC}
   > "J'ai dépensé 50€ hier pour un taxi"
   ```
2. Observe la progression :
   - Intent-Agent classifie et publie sur `intent-output`.
   - Reasoning-Agent consomme, appelle AI-Core, republie sur `reasoning-output`.
   - Reassign-Agent et Audit-Agent poursuivent le flux.
3. Consulte les topics finaux (`kafka-console-consumer`) ou les logs pour valider la chaîne.

---

## 8. Observabilité & tuning

- Tous les services exposent des endpoints Actuator (`/actuator/health`, `/actuator/metrics`) : branche Prometheus/Grafana si besoin.
- Ajuste les timeouts (`AI_CORE_TIMEOUT`, `intent-agent.ai-core.timeout`, etc.) selon tes SLA.
- Resilience4j est déjà présent dans AI-Core : configure les circuits breakers/retries si l’infrastructure LLM ou Weaviate est instable.

---

## 9. Déploiement continu

Automatise :

1. **CI** : lint/test + `mvn package`, `docker build`, `docker push`.
2. **CD** : appliquer ton `docker-compose.yml` ou manifestes Kubernetes avec les secrets/variables injectés depuis ton gestionnaire (Vault, AWS SM, etc.).

Ainsi tu passes du développement local à la prod avec le même pipeline de build et la même configuration.

---

Bonne mise en production ! Grâce à ces étapes, ta stack multi‑agents reste reproductible, sécurisée et observable du premier lancement jusqu’à l’exploitation continue.
