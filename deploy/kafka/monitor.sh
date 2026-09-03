#!/bin/bash

NAMESPACE="agent-system"
BOOTSTRAP_SERVER="kafka.agent-system.svc.cluster.local:9092"

echo "================================================"
echo "  Kafka Cluster Monitoring                     "
echo "================================================"

echo ""
echo "=== Cluster Overview ==="
echo "Pods Status:"
kubectl get pods -n $NAMESPACE -l app=kafka -o wide

echo ""
echo "Services:"
kubectl get svc -n $NAMESPACE -l app=kafka

echo ""
echo "PersistentVolumeClaims:"
kubectl get pvc -n $NAMESPACE | grep kafka

echo ""
echo "=== Resource Usage ==="
kubectl top pods -n $NAMESPACE -l app=kafka 2>/dev/null || echo "Metrics server not available"

echo ""
echo "=== Recent Events ==="
kubectl get events -n $NAMESPACE --field-selector involvedObject.name!=kafka-client --sort-by='.lastTimestamp' | tail -10

echo ""
echo "=== Kafka Cluster Info ==="
echo "Fetching cluster metadata..."
kubectl run kafka-monitor --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- kafka-broker-api-versions --bootstrap-server $BOOTSTRAP_SERVER 2>/dev/null | head -20 || true

echo ""
echo "=== Topics Summary ==="
kubectl run kafka-topics-list --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- kafka-topics --bootstrap-server $BOOTSTRAP_SERVER --list 2>/dev/null || true

echo ""
echo "=== Consumer Groups ==="
kubectl run kafka-groups-list --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- kafka-consumer-groups --bootstrap-server $BOOTSTRAP_SERVER --list 2>/dev/null || true

echo ""
echo "================================================"
echo "  Monitoring Complete                          "
echo "================================================"
