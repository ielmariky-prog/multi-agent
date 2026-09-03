
# 🧠 Multi-Agent AI System — Architecture 2025 (README.md)

## 🚀 Overview

This project implements a **multi-agent distributed architecture** powered by **AI-Core**, a centralized intelligence microservice that orchestrates all interactions with OpenAI (GPT-4o, GPT-4o-mini), Weaviate 5.5 vector search, query rewriting, reranking, and RAG pipelines.  

Each agent (Intent, Reasoning, Reassign, Audit) communicates through **Kafka**, follows the **Thin-Agent Pattern (2025 best practice)**, and delegates all heavy AI processing to AI-Core.

This architecture is designed for advanced automation use-cases:  
✔ expense extraction (notes de frais)  
✔ payslip generation  
✔ invoice generation (facturation client)
✔ HR workflows  
✔ document processing  
✔ enterprise automation

---

# 🧩 Architecture Overview (2025 Best Practices)

```
           ┌──────────────────────────────┐
           │            AI-CORE            │
           │   (Central Intelligence)      │
           │--------------------------------│
           │  - OpenAIClient               │
           │  - RAGService (rewrite/search/rerank)│
           │  - ChunkService               │
           │  - ReRankService              │
           │  - QueryRewriteService        │
           │  - ReasoningService           │
           │  - IntentService              │
           │  - WeaviateService 5.5        │
           └──────────────────────────────┘
                      ▲
                      │ HTTP (WebClient)
 ┌────────────────────┴────────────────────┐
 │                                         │
 ▼                                         ▼
┌────────────────────┐           ┌───────────────────────┐
│  INTENT-AGENT       │ Kafka →   │  REASONING-AGENT      │
│  - consumes input   │           │  - consumes intent     │
│  - calls AI-Core    │           │  - calls AI-Core       │
│  - publishes intent │           │  - publishes reasoning │
└────────────────────┘           └───────────────────────┘
                                           │ Kafka
                                           ▼
                               ┌───────────────────────────┐
                               │     REASSIGN-AGENT        │
                               │ - consumes reasoning       │
                               │ - decides next step        │
                               │ - publishes workflow       │
                               └───────────────────────────┘

                               ┌───────────────────────────┐
                               │       AUDIT-AGENT          │
                               │   - consumes all topics    │
                               │   - stores full history    │
                               └───────────────────────────┘
```

---

# 🧠 AI-Core — Central Intelligence

AI-Core is the single source of truth for all AI operations.

### ✔ Responsibilities
- RAG (Weaviate 5.5)
- Chunking & Embedding
- Query rewriting
- Reranking (Responses API)
- GPT-4o reasoning (JSON Mode)
- Intent classification
- Final decision representation

### ✔ OpenAI Best Practices 2025
- SDK officiel : `com.openai:openai-java:4.8.0`
- GPT-4o for reasoning
- GPT-4o-mini for rewrite / rerank / intent
- JSON Mode mandatory
- Responses API instead of Completions
- text-embedding-3-small for indexing

### ✔ Endpoints exposed by AI-Core
| Endpoint | Description |
|----------|-------------|
| `/reasoning/analyze` | Full RAG + reasoning (GPT-4o JSON) |
| `/intent/classify` | Intent classification |
| `/rag/index` | Chunk + embedding + index into Weaviate |
| `/rag/query` | Search top-K + rerank |
| `/receipts/upload` | Upload de justificatif (image/pdf) → OCR local + extraction JSON + détection de doublons |
| `/invoices/generate` | Génération de facture PDF à partir de données structurées |

### 🧾 OCR & upload justificatifs
- OCR local via Tesseract (`AI_CORE_OCR_TESSERACT_CMD`) et conversion PDF via `pdftoppm` (`AI_CORE_OCR_PDFTOPPM_CMD`).
- Storage local configurable `AI_CORE_RECEIPTS_STORAGE_PATH` (persistant si volume monté en K8s).
- Prompt OCR configurable (`AI_CORE_PROMPT_OCR_SINGLE_EXPENSE`), date de référence = today UTC, blocage des dates futures.
- Détection de doublons : hash binaire + hash texte OCR normalisé (lower/accents retirés).
- Endpoint `POST /receipts/upload` (multipart `file`) renvoie `id`, hashes, flags duplicat, texte OCR, JSON extrait.

### ⚙️ Prérequis OCR (local)
- Installer `tesseract-ocr` (+ langue `fra`) et `poppler-utils` (`pdftoppm`) dans l’image ou sur la VM. Le Dockerfile `ai-core/Dockerfile` inclut déjà ces paquets.
- Variables par défaut dans `ai-core/deploy/k8s/configMap.yaml` : commandes, langue, DPI, chemin de stockage, prompts.
- En K8s, le déploiement `ai-core` monte un volume `/data/receipts` (emptyDir par défaut) ; remplacer par un PVC si nécessaire.

### 📊 Rapports par période — approche hybride (LLM + requête structurée)
Recommandation pour générer des notes de frais entre deux dates :
- Stockage structuré des dépenses (Weaviate ou DB) avec champs : `date` ISO, `amount`, `currency`, `type`, `description`, `originalText`, `source` (receipt|text), `duplicateFlag`, et un champ texte concaténé pour l’embedding.
- Compréhension de la requête (LLM) : extraction d’une période start/end + filtres (type, devise) via un prompt dédié. Fallback période par défaut si parsing impossible.
- Sélection stricte : requête structurée sur `date BETWEEN start AND end` (et filtres) pour récupérer les dépenses éligibles. Pas de dépendance au ranking sémantique pour l’inclusion/exclusion.
- RAG sur le sous-ensemble : récupérer le texte/embedding des dépenses filtrées, rerank si besoin (top-K) pour la synthèse.
- Synthèse LLM : prompt de résumé “tu résumes uniquement les dépenses fournies” pour calculer totaux par type/devise, lister les lignes principales, signaler les doublons (via `duplicateFlag`).
- Dédoublonnage : exploiter les flags calculés à l’ingestion (hash binaire/texte) pour exclure ou marquer les doublons avant la synthèse.

---

# 🤖 Agent Architecture (Thin Agents 2025)

## 1. Intent-Agent
Consumes:
- `input-topic`

Calls:
- AI-Core `/intent/classify`

Produces:
- `intent-output-topic`

Responsibility:
➡ Detects intent (expense, payroll, HR request…)

---

## 2. Reasoning-Agent
Consumes:
- `intent-output-topic`

Calls:
- AI-Core `/reasoning/analyze`

Produces:
- `reassign-input-topic`

Responsibility:
➡ Performs deep reasoning on the classified intent using RAG + GPT-4o.

---

## 3. Reassign-Agent
Consumes:
- `reassign-input-topic`

Produces:
- `workflow-output-topic`

Responsibility:
➡ Decides next action (ex: create expense, validate data, escalate…).

---

## 4. Invoice Generation (New)
To generate an invoice:
1. Publish text to `input-topic`: "Facture le client X pour 5 jours de dev..."
2. **Intent-Agent** detects `generate_invoice`.
3. **Reasoning-Agent** uses `AI_CORE_PROMPT_INVOICE` to extract structured JSON (Client, Items, VAT, Totals) via the `/reasoning/analyze` endpoint.
4. **Reassign-Agent** consumes the structured data and can trigger a workflow, such as calling the `POST /invoices/generate` endpoint on AI-Core to create a PDF file.
5. The generated invoice data is indexed in Weaviate under the `Invoice` class.

---

# 📌 Notes de frais & Factures

## Génération de Factures (PDF)

L'architecture permet de générer des factures structurées à partir d'un texte libre.

- **Endpoint** : `POST /invoices/generate`
- **Input (JSON)** :
  ```json
  {
    "clientName": "ACME Corp",
    "clientAddress": "123 Main Street, Anytown",
    "invoiceNumber": "FACT-2026-001",
    "date": "2026-03-15",
    "dueDate": "2026-04-15",
    "items": [
      { "description": "Développement back-end", "quantity": 5, "unitPrice": 600.00 },
      { "description": "Gestion de projet", "quantity": 1, "unitPrice": 800.00 }
    ],
    "vatRate": 20.0,
    "currency": "EUR"
  }
  ```
- **Processus** :
  1. Le `Reasoning-Agent` appelle AI-Core pour extraire ces données structurées depuis un texte libre ("Facture ACME pour 5 jours de dev...").
  2. Le `Reassign-Agent` reçoit la structure et peut appeler l'endpoint `/invoices/generate` de AI-Core.
  3. AI-Core utilise un template (par exemple avec Thymeleaf ou JasperReports) pour générer un PDF.
  4. Le PDF généré est stocké (localement ou sur un S3) et son ID/URL est retourné.
  5. L'objet facture est également indexé dans Weaviate avec la classe `Invoice`.

### Indexation Weaviate (Classe `Invoice`)
Les factures sont stockées dans Weaviate pour permettre des recherches sémantiques ou des rapports.

- **Classe** : `Invoice`
- **Propriétés** :
  - `clientName` (text)
  - `invoiceNumber` (text)
  - `date` (date)
  - `dueDate` (date)
  - `totalAmount` (number)
  - `vatAmount` (number)
  - `status` (text, ex: DRAFT, SENT, PAID)
  - `itemsJson` (text, le JSON des lignes de facture)
  - `sourceText` (text, le texte original de la demande)

## Ajouter une note de frais (texte ou justificatif)
- **Texte libre** : publier sur `intent-input-topic` (Kafka) ou appeler `POST /intent/classify` puis `/reasoning/analyze` avec le texte. Les types gérés par prompt : `restaurant`, `boulangerie`, `café`, `hôtel`, `taxi/transport`, `carburant`, `péage`, `matériel informatique/PC`, `location` (domiciliation, location fixe), `abonnement`, `frais_km`, `autre`.
- **Justificatif** : `POST /receipts/upload` (multipart `file`). OCR local, extraction JSON (date ISO, amount, currency, type, description, paymentMode, address, km), détection de doublons.
- Dates relatives : “hier”, “avant-hier”, “il y a X jours”, “ce mois-ci” → normalisées en UTC (YYYY-MM-DD). Si mois sans jour, date au 1er du mois (année courante). Pas de dates futures (bloquées ou ramenées à today).
- Champs par défaut : `paymentMode` = Personnel si absent, `currency` = EUR si absent, `address` = “inconnue” si absente.

## Supprimer une note de frais
- **Par id** : publier une demande “supprime la note 15” (ou `/reasoning/analyze` avec `intent=delete_expense` et `entities.ids=[15]`). La suppression se fait sur `expenseId` (Weaviate) ; sans mois/date, on supprime par id seul.
- **Par date** : “supprime la note d’hier” → suppression par date (toutes les notes de ce jour).

## Export Excel (dépenses entre deux dates)
- Endpoint : `GET /expenses/report/excel?start=YYYY-MM-DD&end=YYYY-MM-DD&workingDays=<n>` → renvoie `rapport-notes-frais.xlsx`.
- Endpoint : `GET /expenses/report/pdf?start=YYYY-MM-DD&end=YYYY-MM-DD&workingDays=<n>` → renvoie `rapport-notes-frais.pdf`.
- Onglet **Depenses** : tri par date, colonnes `id, date (formatée), amount, currency, type, paymentMode, address, description, km`. IDs sont déduits de Weaviate; si un id manque/dupliqué, un id unique est affecté à l’export.
- Onglet **Synthese** : totaux perso/business hors km/location, total location, km et coût km calculés uniquement sur les lignes `frais_km` présentes, total général, et “Total à rembourser (Personnel, incluant location & km)” en rouge/gras.
- En-tête : titre “Rapport de notes de frais”, entreprise “IA-INSIGHT”, mois (libellé FR) basé sur la période.

## 4. Audit-Agent
Consumes:
- all Kafka topics  
Responsibility:  
➡ Stores historical events for full traceability.

No AI. No RAG. No OpenAI.

---

# 🧩 Kafka Topics (Standardized)

| Topic | Producer | Consumer |
|--------|---------|-----------|
| `input-topic` | External Input | intent-agent |
| `intent-output-topic` | intent-agent | reasoning-agent |
| `reassign-input-topic` | reasoning-agent | reassign-agent |
| `workflow-output-topic` | reassign-agent | external systems |
| `audit.*` | all | audit-agent |

---

# 🧠 RAG Pipeline (Modern 2025)

RAG is centralized in AI-Core and follows:

1. **Query Rewrite** (GPT-4o-mini)
2. **Weaviate search (vector + BM25 hybrid)**
3. **Re-Rank with LLM** (`gpt-4o-mini`, Responses API)
4. **Context construction**
5. **Final reasoning (GPT-4o JSON Mode)**

This ensures **high accuracy**, **low costs**, and **consistent results**.

---

# 🧱 Technologies Used

| Layer | Technology |
|--------|------------|
| LLM | OpenAI GPT-4o / GPT-4o-mini |
| RAG | Weaviate 5.5 |
| Messaging | Kafka |
| Framework | Spring Boot 3.3.4 |
| Java | JDK 21 |
| Network | WebClient (WebFlux) |
| Architecture | Multi-Agent Microservices |
| Format | JSON Strict Mode |

---

# 🏛️ Architectural Principles (Best Practices 2025)

- **Thin Agent Pattern** : all AI centralized in AI-Core  
- **JSON Mode only** : predictable, safe, structured  
- **Retry + Circuit Breaker** using WebClient  
- **Vector DB shared (single Weaviate)**  
- **Global chunk store in AI-Core**  
- **Microservices isolated by responsibility**  
- **Kafka-driven orchestration**  
- **Stateless services** (AI-Core keeps no session)

---
# use case
[User] → "J’ai payé 100€ au restaurant"

Reasoning-Agent :
  ✔ publie → documents.raw       (pour indexation → RAG)
  ✔ publie → reasoning-input     (pour pipeline IA)

AI-Core lit documents.raw :
  → chunk
  → embedding
  → weaviate.index()

Reasoning-Agent lit reasoning-input :
  → rewrite
  → semantic search
  → rerank
  → extraction expense
  → publie intent-output

Intent-Agent lit intent-output :
  → modèle Expense généré
  → publie reassign-input

Reassign-Agent lit reassign-input :
  → prend la décision

# 📜 License
MIT or custom — your choice.

---

# 📬 Contact
For support or collaboration, contact the architect of this system.
# multi-agent-openai-kafka
