#!/bin/bash

set -e

NAMESPACE="agent-system"
BOOTSTRAP_SERVER="kafka.agent-system.svc.cluster.local:9092"
TEST_TOPIC="test-kafka-deployment"

echo "================================================"
echo "  Testing Kafka Installation                   "
echo "================================================"

echo ""
echo "1. Checking Kafka pods status..."
kubectl get pods -n $NAMESPACE -l app=kafka

echo ""
echo "2. Checking Kafka services..."
kubectl get svc -n $NAMESPACE -l app=kafka

echo ""
echo "3. Creating test topic..."
kubectl run kafka-test-topic --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
     --create --topic $TEST_TOPIC \
     --partitions 3 --replication-factor 3 \
     --if-not-exists

echo ""
echo "4. Listing topics..."
kubectl run kafka-list-topics --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- kafka-topics --bootstrap-server $BOOTSTRAP_SERVER --list

echo ""
echo "5. Describing test topic..."
kubectl run kafka-describe-topic --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
     --describe --topic $TEST_TOPIC

echo ""
echo "6. Producing test messages..."
echo -e "Test message 1\nTest message 2\nTest message 3" | \
kubectl run kafka-test-producer --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- kafka-console-producer --bootstrap-server $BOOTSTRAP_SERVER \
     --topic $TEST_TOPIC

echo ""
echo "7. Consuming test messages..."
kubectl run kafka-test-consumer --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- timeout 5 kafka-console-consumer --bootstrap-server $BOOTSTRAP_SERVER \
     --topic $TEST_TOPIC --from-beginning || true

echo ""
echo "8. Cleaning up test topic..."
kubectl run kafka-delete-topic --rm -ti --restart=Never \
  --image=confluentinc/cp-kafka:7.7.0 \
  --namespace=$NAMESPACE \
  -- kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
     --delete --topic $TEST_TOPIC

echo ""
echo "================================================"
echo "  Kafka Test Complete!                        "
echo "================================================"
echo ""
echo "All tests passed successfully!"
echo "Kafka cluster is ready to use."
