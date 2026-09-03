# 🎯 Kafka Commands Cheat Sheet

Guide rapide des commandes Kafka les plus utilisées.

## 📋 Table des Matières

- [Variables d'Environnement](#variables-denvironnement)
- [Topics](#topics)
- [Producers & Consumers](#producers--consumers)
- [Consumer Groups](#consumer-groups)
- [Cluster Management](#cluster-management)
- [Debugging](#debugging)
- [Kubernetes](#kubernetes)

---

## Variables d'Environnement

```bash
# Bootstrap server
export BOOTSTRAP_SERVER="kafka.agent-system.svc.cluster.local:9092"
export NAMESPACE="agent-system"

# Image Kafka
export KAFKA_IMAGE="confluentinc/cp-kafka:7.7.0"
```

---

## Topics

### Créer un Topic

```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=$KAFKA_IMAGE --namespace=$NAMESPACE \
  -- kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
     --create --topic NOM_DU_TOPIC \
     --partitions 3 --replication-factor 3
```

### Lister les Topics

```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=$KAFKA_IMAGE --namespace=$NAMESPACE \
  -- kafka-topics --bootstrap-server $BOOTSTRAP_SERVER --list
```

### Décrire un Topic

```bash
kubectl run kafka-client --rm -ti --restart=Never \
  --image=$KAFKA_IMAGE --namespace=$NAMESPACE \
  -- kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
     --describe --topic NOM_DU_TOPIC
```

### Health Check

```bash
# Pods status
kubectl get pods -n $NAMESPACE -l app=kafka
```

---

**Astuce**: Créez des alias dans votre shell pour les commandes fréquentes !
