# Kafka KRaft - Quick Start Guide

## Installation Rapide

```bash
cd /Users/younes/dev/multi-agent-openai-kafka/deploy/kafka
./install.sh
```

## Vérification

```bash
kubectl get pods -n agent-system -l app=kafka
```

Attendez que tous les pods soient `Running` et `1/1 Ready`.

## Test de Base

```bash
./test-kafka.sh
```

## Connexion au Cluster

**Bootstrap Server**: `kafka.agent-system.svc.cluster.local:9092`

## Commandes Essentielles

### Créer un topic
```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --create --topic mon-topic --partitions 3 --replication-factor 3
```

### Lister les topics
```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 --list
```

### Produire des messages
```bash
kubectl run kafka-producer --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-console-producer --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --topic mon-topic
```

### Consommer des messages
```bash
kubectl run kafka-consumer --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-console-consumer --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --topic mon-topic --from-beginning
```

## Monitoring

```bash
./monitor.sh
```

## Logs

```bash
# Logs en temps réel d'un pod
kubectl logs -f kafka-0 -n agent-system

# Logs des 3 pods
kubectl logs -l app=kafka -n agent-system --tail=50
```

## Désinstallation

```bash
./uninstall.sh
```

## Configuration du Cluster

| Paramètre | Valeur |
|-----------|--------|
| Nœuds | 3 (broker + controller) |
| CPU par nœud | 1-2 cores |
| Mémoire par nœud | 2-4Gi |
| Stockage par nœud | 20Gi |
| Réplication par défaut | 3 |
| Min ISR | 2 |
| Rétention | 7 jours / 1GB par partition |

## Troubleshooting

### Les pods ne démarrent pas
```bash
kubectl describe pod kafka-0 -n agent-system
kubectl logs kafka-0 -n agent-system
```

### Problème de connexion
```bash
kubectl get svc -n agent-system
kubectl run test --rm -ti --restart=Never --image=busybox --namespace=agent-system \
  -- nc -zv kafka.agent-system.svc.cluster.local 9092
```

### Reset complet
```bash
./uninstall.sh
# Répondre "y" pour supprimer les PVC
./install.sh
```

## Documentation Complète

Voir `README.md` pour la documentation détaillée.

## Support

- Kafka Documentation: https://kafka.apache.org/documentation/
- Confluent Platform: https://docs.confluent.io/
- KRaft Mode: https://kafka.apache.org/documentation/#kraft
