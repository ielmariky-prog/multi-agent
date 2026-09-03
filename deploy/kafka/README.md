# Kafka KRaft Deployment

Configuration complète pour déployer Apache Kafka en mode KRaft (sans Zookeeper) avec 3 nœuds combinés (broker + controller).

## Architecture

- **Version**: Kafka 7.7.0 (Compatible avec Kafka 4.1.0)
- **Mode**: KRaft (Kafka Raft)
- **Nœuds**: 3 nœuds combinés (chaque nœud est à la fois broker et controller)
- **Stockage**: 20Gi par nœud (PersistentVolumeClaim)
- **Namespace**: agent-system

## Spécifications des Ressources

### Par Nœud:
- **CPU**:
  - Request: 1 core (1000m)
  - Limit: 2 cores (2000m)
- **Mémoire**:
  - Request: 2Gi
  - Limit: 4Gi
- **JVM Heap**: 1536m (Xms et Xmx)
- **Stockage**: 20Gi persistant

### Configuration Optimisée:
- **Threads réseau**: 8
- **Threads I/O**: 8
- **Buffer d'envoi**: 100KB
- **Buffer de réception**: 100KB
- **GC**: G1GC avec optimisations

## Installation

### Prérequis
- kubectl configuré
- Accès au cluster Kubernetes
- Namespace `agent-system` (créé automatiquement si absent)

### Déploiement

```bash
cd /Users/younes/dev/multi-agent-openai-kafka/deploy/kafka
./install.sh
```

Le script va:
1. Créer le namespace si nécessaire
2. Générer un cluster ID unique
3. Déployer les services et le StatefulSet
4. Attendre que tous les pods soient prêts

### Vérification

```bash
# Vérifier les pods
kubectl get pods -n agent-system -l app=kafka

# Vérifier les PVC
kubectl get pvc -n agent-system

# Vérifier les services
kubectl get svc -n agent-system -l app=kafka

# Logs d'un pod
kubectl logs kafka-0 -n agent-system
```

## Configuration Kafka

### Réplication
- **Facteur de réplication par défaut**: 3
- **Min ISR**: 2
- **Partitions par défaut**: 3

### Rétention
- **Durée**: 168 heures (7 jours)
- **Taille**: 1GB par partition
- **Segment**: 1GB

### Topics Internes
- **Offsets topic**: 
  - Réplication: 3
  - Partitions: 50
- **Transaction log**:
  - Réplication: 3
  - Min ISR: 2

## Connexion à Kafka

### Depuis un pod dans le cluster

```bash
# Service DNS
kafka.agent-system.svc.cluster.local:9092
```

### Depuis un pod temporaire

```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- bash

# Une fois dans le pod
kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 --list
```

## Opérations Courantes

### Créer un topic

```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --create --topic test-topic --partitions 3 --replication-factor 3
```

### Lister les topics

```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 --list
```

### Décrire un topic

```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --describe --topic test-topic
```

### Produire des messages

```bash
kubectl run kafka-producer --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-console-producer --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --topic test-topic
```

### Consommer des messages

```bash
kubectl run kafka-consumer --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-console-consumer --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --topic test-topic --from-beginning
```

## Monitoring

### Vérifier l'état du cluster

```bash
# Status des pods
kubectl get pods -n agent-system -l app=kafka -o wide

# Métriques des pods
kubectl top pods -n agent-system -l app=kafka

# Describe un pod
kubectl describe pod kafka-0 -n agent-system
```

### Logs

```bash
# Logs d'un pod spécifique
kubectl logs kafka-0 -n agent-system

# Logs en temps réel
kubectl logs -f kafka-0 -n agent-system

# Logs des 3 nœuds
kubectl logs -l app=kafka -n agent-system --tail=100
```

## Haute Disponibilité

### Features
- **Anti-affinity**: Les pods sont distribués sur différents nœuds
- **Pod Disruption Budget**: Maximum 1 pod indisponible à la fois
- **Replication**: Données répliquées sur 3 nœuds
- **Min ISR**: Garantit au moins 2 répliques synchronisées

### Tolérance aux pannes
- Le cluster peut tolérer la perte d'1 nœud
- Avec Min ISR=2, les écritures nécessitent 2 nœuds disponibles
- Les lectures peuvent continuer avec un seul nœud

## Scaling

### Augmenter le nombre de nœuds

```bash
kubectl scale statefulset kafka -n agent-system --replicas=5
```

**Note**: Vous devrez mettre à jour `KAFKA_CONTROLLER_QUORUM_VOTERS` pour inclure les nouveaux nœuds.

### Augmenter les ressources

Éditez le StatefulSet:
```bash
kubectl edit statefulset kafka -n agent-system
```

Modifiez les sections `resources.requests` et `resources.limits`.

## Désinstallation

```bash
cd /Users/younes/dev/multi-agent-openai-kafka/deploy/kafka
./uninstall.sh
```

Le script vous demandera si vous souhaitez également supprimer les PVC (données).

### Désinstallation manuelle

```bash
# Supprimer le StatefulSet
kubectl delete statefulset kafka -n agent-system

# Supprimer les services
kubectl delete service kafka kafka-headless -n agent-system

# Supprimer les PVC (ATTENTION: perte de données)
kubectl delete pvc -l app=kafka -n agent-system
```

## Troubleshooting

### Les pods ne démarrent pas

```bash
# Vérifier les événements
kubectl get events -n agent-system --sort-by='.lastTimestamp'

# Describe le pod
kubectl describe pod kafka-0 -n agent-system

# Vérifier les PVC
kubectl get pvc -n agent-system
```

### Problèmes de connexion

```bash
# Vérifier les services
kubectl get svc -n agent-system

# Tester la connectivité depuis un pod
kubectl run test-pod --rm -ti --restart=Never \
  --image=busybox \
  --namespace=agent-system \
  -- nc -zv kafka.agent-system.svc.cluster.local 9092
```

### Problèmes de quorum

```bash
# Vérifier que les 3 nœuds sont en cours d'exécution
kubectl get pods -n agent-system -l app=kafka

# Vérifier les logs pour les erreurs de quorum
kubectl logs kafka-0 -n agent-system | grep -i "quorum\|controller"
```

## Performance Tuning

### Pour plus de throughput
- Augmenter `num.io.threads` et `num.network.threads`
- Augmenter `socket.send.buffer.bytes` et `socket.receive.buffer.bytes`
- Utiliser compression (déjà configuré)

### Pour plus de rétention
- Augmenter `log.retention.hours`
- Augmenter `log.retention.bytes`
- Augmenter la taille des PVC

### Pour réduire la latence
- Réduire `linger.ms` côté producer
- Utiliser `acks=1` au lieu de `acks=all` (moins de durabilité)

## Sécurité

**Note**: Cette configuration utilise PLAINTEXT (pas de chiffrement).

Pour la production, considérez:
- Activer SSL/TLS
- Activer SASL pour l'authentification
- Activer les ACLs
- Utiliser des Network Policies

## Fichiers

- `statefulset.yaml`: Définition du StatefulSet et des services
- `values-kraft.yaml`: Valeurs de configuration (référence)
- `install.sh`: Script d'installation
- `uninstall.sh`: Script de désinstallation
- `README.md`: Cette documentation

## Support

Pour les problèmes ou questions:
1. Vérifier les logs: `kubectl logs -l app=kafka -n agent-system`
2. Vérifier les événements: `kubectl get events -n agent-system`
3. Consulter la documentation Kafka: https://kafka.apache.org/documentation/
