# Kafka KRaft Architecture

## Vue d'ensemble du cluster

```
┌─────────────────────────────────────────────────────────────────┐
│                      Namespace: agent-system                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     Service: kafka (ClusterIP)                   │
│                    kafka.agent-system.svc.cluster.local:9092     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              Service: kafka-headless (Headless)                  │
│                  kafka-headless.agent-system.svc                 │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│    kafka-0       │  │    kafka-1       │  │    kafka-2       │
│                  │  │                  │  │                  │
│  Combined Node   │  │  Combined Node   │  │  Combined Node   │
│  (Broker +       │  │  (Broker +       │  │  (Broker +       │
│   Controller)    │  │   Controller)    │  │   Controller)    │
│                  │  │                  │  │                  │
│  Node ID: 0      │  │  Node ID: 1      │  │  Node ID: 2      │
│                  │  │                  │  │                  │
│  Ports:          │  │  Ports:          │  │  Ports:          │
│  - 9092 Client   │  │  - 9092 Client   │  │  - 9092 Client   │
│  - 9093 Ctrl     │  │  - 9093 Ctrl     │  │  - 9093 Ctrl     │
│  - 9094 Internal │  │  - 9094 Internal │  │  - 9094 Internal │
│                  │  │                  │  │                  │
│  Resources:      │  │  Resources:      │  │  Resources:      │
│  CPU: 1-2 cores  │  │  CPU: 1-2 cores  │  │  CPU: 1-2 cores  │
│  RAM: 2-4Gi      │  │  RAM: 2-4Gi      │  │  RAM: 2-4Gi      │
│  Heap: 1536m     │  │  Heap: 1536m     │  │  Heap: 1536m     │
│                  │  │                  │  │                  │
│       ▼          │  │       ▼          │  │       ▼          │
│  ┌──────────┐   │  │  ┌──────────┐   │  │  ┌──────────┐   │
│  │   PVC    │   │  │  │   PVC    │   │  │  │   PVC    │   │
│  │  20Gi    │   │  │  │  20Gi    │   │  │  │  20Gi    │   │
│  └──────────┘   │  │  └──────────┘   │  │  └──────────┘   │
└──────────────────┘  └──────────────────┘  └──────────────────┘
         ▲                     ▲                     ▲
         └──────────────────┬──┴─────────────────────┘
                            │
                    KRaft Quorum
                (Raft consensus protocol)
```

## Composants

### 1. Services Kubernetes

#### Service Principal (kafka)
- **Type**: ClusterIP
- **Port**: 9092
- **Usage**: Point d'entrée pour les clients Kafka
- **Load balancing**: Round-robin vers les 3 brokers

#### Service Headless (kafka-headless)
- **Type**: Headless (ClusterIP: None)
- **Ports**: 9092 (client), 9093 (controller), 9094 (internal)
- **Usage**: 
  - DNS pour la découverte des nœuds
  - Communication inter-brokers
  - Élection du leader KRaft

### 2. StatefulSet (kafka)

#### Caractéristiques
- **Replicas**: 3
- **Pod Management**: Parallel (démarrage simultané)
- **Update Strategy**: RollingUpdate
- **Anti-affinity**: Distribution sur différents nœuds

#### Nommage des Pods
```
kafka-0.kafka-headless.agent-system.svc.cluster.local
kafka-1.kafka-headless.agent-system.svc.cluster.local
kafka-2.kafka-headless.agent-system.svc.cluster.local
```

### 3. Nœuds Kafka (Combined Mode)

Chaque nœud joue deux rôles:

#### Rôle Broker
- Gestion des partitions
- Stockage des messages
- Réplication des données
- Serving des clients (producers/consumers)

#### Rôle Controller
- Participation au quorum KRaft
- Gestion des métadonnées du cluster
- Coordination des élections de leaders
- Gestion de la configuration du cluster

### 4. Stockage Persistant

#### PersistentVolumeClaims (PVC)
- **Nom**: data-kafka-{0,1,2}
- **Taille**: 20Gi par nœud
- **Access Mode**: ReadWriteOnce
- **Storage Class**: Default du cluster
- **Mount Path**: /var/lib/kafka/data

## Flux de Données

### 1. Écriture (Producer)

```
Producer
   │
   │ Connect to: kafka.agent-system.svc.cluster.local:9092
   │
   ▼
Service (kafka) - Load Balancer
   │
   │ Route to broker with leader partition
   │
   ▼
Broker Leader (e.g., kafka-1)
   │
   │ Write to local log
   │
   ├──► Replicate to kafka-0 (follower)
   │
   └──► Replicate to kafka-2 (follower)
        │
        │ Wait for Min ISR (2 replicas)
        │
        ▼
   ACK to Producer
```

### 2. Lecture (Consumer)

```
Consumer
   │
   │ Connect to: kafka.agent-system.svc.cluster.local:9092
   │
   ▼
Service (kafka) - Load Balancer
   │
   │ Route to any broker
   │
   ▼
Broker (any)
   │
   │ Fetch metadata (leader partition info)
   │
   ▼
Consumer connects to leader partition
   │
   │ Read from log
   │
   ▼
Messages to Consumer
```

### 3. KRaft Consensus

```
Controller Quorum (kafka-0, kafka-1, kafka-2)
   │
   │ Port 9093 (CONTROLLER listener)
   │
   ├─► Leader Election (Raft)
   │   │
   │   ├─► kafka-0: Leader
   │   ├─► kafka-1: Follower
   │   └─► kafka-2: Follower
   │
   ├─► Metadata Log Replication
   │   │
   │   └─► All metadata changes replicated via Raft
   │
   └─► Cluster Coordination
       │
       ├─► Partition assignments
       ├─► Topic creation/deletion
       ├─► Configuration changes
       └─► Broker registration
```

## Réplication et Haute Disponibilité

### Configuration de Réplication

```
Topic: example-topic
Partitions: 3
Replication Factor: 3

Partition 0:
  Leader: kafka-0
  Replicas: [kafka-0, kafka-1, kafka-2]
  ISR: [kafka-0, kafka-1, kafka-2]

Partition 1:
  Leader: kafka-1
  Replicas: [kafka-1, kafka-2, kafka-0]
  ISR: [kafka-1, kafka-2, kafka-0]

Partition 2:
  Leader: kafka-2
  Replicas: [kafka-2, kafka-0, kafka-1]
  ISR: [kafka-2, kafka-0, kafka-1]
```

### Scénarios de Panne

#### Perte d'un nœud (e.g., kafka-2)

```
Avant:
kafka-0: ✓ (Leader Partition 0)
kafka-1: ✓ (Leader Partition 1)
kafka-2: ✓ (Leader Partition 2)

Après:
kafka-0: ✓ (Leader Partition 0, New Leader Partition 2)
kafka-1: ✓ (Leader Partition 1)
kafka-2: ✗ (Down)

Status:
- Cluster opérationnel
- Données toujours disponibles (Min ISR = 2)
- Performance réduite
- Réplication en cours vers 2 nœuds
```

#### Perte de deux nœuds (e.g., kafka-1 et kafka-2)

```
Avant:
kafka-0: ✓
kafka-1: ✓
kafka-2: ✓

Après:
kafka-0: ✓ (Seul survivant)
kafka-1: ✗ (Down)
kafka-2: ✗ (Down)

Status:
- Cluster en lecture seule
- Écritures bloquées (Min ISR = 2 non satisfait)
- Lectures possibles
- Quorum KRaft perdu
```

## Réseau et Listeners

### Listeners Configuration

```
┌────────────────────────────────────────────────────────┐
│                    kafka-0 Pod                          │
│                                                         │
│  PLAINTEXT://kafka-0:9092    ◄── Clients externes      │
│  CONTROLLER://kafka-0:9093   ◄── KRaft quorum          │
│  INTERNAL://kafka-0:9094     ◄── Inter-broker          │
│                                                         │
└────────────────────────────────────────────────────────┘
```

### Security Protocol Map

```
PLAINTEXT:PLAINTEXT   - Client connections (no encryption)
CONTROLLER:PLAINTEXT  - Controller quorum (no encryption)
INTERNAL:PLAINTEXT    - Inter-broker (no encryption)
```

## Performance et Tuning

### Thread Model

```
Per Broker:
├─► Network Threads: 8
│   └─► Handle socket I/O
│
├─► I/O Threads: 8
│   └─► Read/Write disk operations
│
├─► Request Handler Threads: Auto
│   └─► Process requests
│
└─► Background Threads
    ├─► Log Cleaner
    ├─► Replication Fetcher
    └─► Controller Event Processor
```

### Memory Allocation

```
Total Memory per Pod: 2-4Gi

├─► JVM Heap: 1536m
│   ├─► Young Generation: ~512m
│   └─► Old Generation: ~1024m
│
├─► Off-Heap Memory: ~1Gi
│   ├─► Direct Buffers
│   ├─► Network Buffers
│   └─► Memory-mapped files
│
└─► OS Cache: Remaining
    └─► Page cache for log segments
```

### Disk Layout

```
PVC (20Gi)
└─► /var/lib/kafka/data/
    ├─► __cluster_metadata-0/
    │   └─► KRaft metadata log
    │
    ├─► topic1-0/
    │   ├─► 00000000000000000000.log
    │   ├─► 00000000000000000000.index
    │   └─► 00000000000000000000.timeindex
    │
    ├─► topic1-1/
    ├─► topic2-0/
    └─► ...
```

## Monitoring Points

### Pod Level
- CPU utilization
- Memory usage
- Network I/O
- Disk I/O
- Disk usage

### Kafka Metrics
- Broker JVM metrics
- Request latency
- Throughput (bytes/sec)
- Active connections
- Under-replicated partitions
- Leader elections
- ISR shrinks/expansions

### Cluster Health
- All brokers online
- Quorum health
- Replication lag
- Partition distribution
- Consumer lag

## Évolutivité

### Scaling Up (Vertical)

```
Augmenter les ressources par pod:
- CPU: 1-2 → 2-4 cores
- Memory: 2-4Gi → 4-8Gi
- Heap: 1536m → 3072m
- Storage: 20Gi → 50-100Gi
```

### Scaling Out (Horizontal)

```
Ajouter des brokers:
replicas: 3 → 5

Nécessite:
1. Mise à jour KAFKA_CONTROLLER_QUORUM_VOTERS
2. Ajout des nouveaux nœuds dans le quorum
3. Rebalancing des partitions
```

## Sécurité (Non implémenté dans cette config)

### À considérer pour la production:

```
┌─────────────────────────────────────┐
│ Client                               │
│   │                                  │
│   │ TLS/SSL                         │
│   │ SASL (PLAIN/SCRAM/GSSAPI)      │
│   ▼                                  │
│ Kafka Broker                         │
│   │                                  │
│   │ ACLs                            │
│   │ Authorization                   │
│   ▼                                  │
│ Topics/Data                          │
└─────────────────────────────────────┘
```

---

**Note**: Cette architecture est optimisée pour un environnement de développement/test avec possibilité d'évolution vers la production.
