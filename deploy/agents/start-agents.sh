#!/usr/bin/env bash
set -euo pipefail

# Apply configs/secrets/deployments for ai-core, reasoning, reassign, audit.

kubectl >/dev/null 2>&1 || { echo "kubectl introuvable"; exit 1; }
#instal topics for multi-agent
kubectl create ns multi-agent

kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --create --topic intent-input-topic \
     --partitions 3 --replication-factor 3

kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --create --topic reasoning-input-topic \
     --partitions 3 --replication-factor 3

kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --create --topic audit.events.in \
     --partitions 3 --replication-factor 3

kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --create --topic reassign-input-topic \
     --partitions 3 --replication-factor 3

kubectl run kafka-client --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=agent-system \
  -- kafka-topics --bootstrap-server kafka.agent-system.svc.cluster.local:9092 \
     --create --topic audit.events.out \
     --partitions 3 --replication-factor 3

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

MANIFESTS=(
  "$ROOT/ai-core/deploy/k8s/configMap.yaml"
  "$ROOT/ai-core/deploy/k8s/secret.yaml"
  "$ROOT/ai-core/deploy/k8s/deployment.yaml"
  "$ROOT/reasoning-agent/deploy/k8s/configMap.yaml"
  "$ROOT/reasoning-agent/deploy/k8s/deployment.yaml"
  "$ROOT/intent-agent//deploy/k8s/configMap.yaml"
  "$ROOT/intent-agent/deploy/k8s/deployment.yaml"
  "$ROOT/reassign-agent//deploy/k8s/configMap.yaml"
  "$ROOT/reassign-agent/deploy/k8s/deployment.yaml"
  "$ROOT/audit-agent//deploy/k8s/configMap.yaml"
  "$ROOT/audit-agent/deploy/k8s/deployment.yaml"
)

for f in "${MANIFESTS[@]}"; do
  echo "kubectl apply -f $f"
  kubectl apply -f "$f"
done

kubectl get pod -n multi-agent -w
