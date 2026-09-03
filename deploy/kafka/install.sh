#!/bin/bash

set -e

NAMESPACE="agent-system"
KAFKA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANIFEST="$KAFKA_DIR/statefulset.yaml"
CONFIGMAP_NAME="kafka-cluster-id"
CONFIGMAP_KEY="cluster.id"
PVC_BASE_NAME="data-kafka"
REPLICAS=3

get_configmap_cluster_id() {
    kubectl get configmap "$CONFIGMAP_NAME" -n "$NAMESPACE" -o "jsonpath={.data.${CONFIGMAP_KEY}}" 2>/dev/null || true
}

read_cluster_id_from_pvc() {
    local pvc_name="$1"
    if ! kubectl get pvc "$pvc_name" -n "$NAMESPACE" >/dev/null 2>&1; then
        return 1
    fi

    local job="kafka-clusterid-reader-$(date +%s)"
    cat <<EOF | kubectl apply -n "$NAMESPACE" -f - >/dev/null
apiVersion: batch/v1
kind: Job
metadata:
  name: $job
spec:
  template:
    spec:
      restartPolicy: Never
      containers:
      - name: reader
        image: busybox:1.36
        command: ["/bin/sh", "-c", "cat /mnt/meta.properties | grep '^cluster.id=' | head -n1 | cut -d= -f2"]
        volumeMounts:
        - name: data
          mountPath: /mnt
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: $pvc_name
  backoffLimit: 0
EOF

    if ! kubectl wait --for=condition=complete "job/$job" -n "$NAMESPACE" --timeout=60s >/dev/null 2>&1; then
        kubectl delete job "$job" -n "$NAMESPACE" --ignore-not-found >/dev/null 2>&1
        return 1
    fi

    local pod
    pod=$(kubectl get pods -n "$NAMESPACE" -l "job-name=$job" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
    local cluster_id=""
    if [[ -n "$pod" ]]; then
        cluster_id=$(kubectl logs -n "$NAMESPACE" "$pod" 2>/dev/null | tr -d '\r\n')
    fi

    kubectl delete job "$job" -n "$NAMESPACE" --ignore-not-found >/dev/null 2>&1

    if [[ -n "$cluster_id" ]]; then
        echo "$cluster_id"
        return 0
    fi
    return 1
}

find_cluster_id_from_pvcs() {
    local idx
    for idx in $(seq 0 $((REPLICAS-1))); do
        local pvc_name="${PVC_BASE_NAME}-${idx}"
        local cid
        cid=$(read_cluster_id_from_pvc "$pvc_name" || true)
        if [[ -n "$cid" ]]; then
            echo "$cid"
            return 0
        fi
    done
    return 1
}

apply_cluster_id_configmap() {
    local cluster_id="$1"
    kubectl create configmap "$CONFIGMAP_NAME" \
        --from-literal="${CONFIGMAP_KEY}=${cluster_id}" \
        -n "$NAMESPACE" \
        --dry-run=client -o yaml | kubectl apply -f -
}

echo "================================================"
echo "  Kafka KRaft Installation (3 Combined Nodes)  "
echo "================================================"

# Check if namespace exists
if ! kubectl get namespace $NAMESPACE &> /dev/null; then
    echo "Creating namespace: $NAMESPACE"
    kubectl create namespace $NAMESPACE
else
    echo "Namespace $NAMESPACE already exists"
fi

# Resolve Cluster ID in priority order:
# 1) Any existing PVC content (avoid mismatch if some brokers already formatted)
# 2) Existing ConfigMap
# 3) Generate new
CM_CLUSTER_ID="$(get_configmap_cluster_id)"
PVC_CLUSTER_ID="$(find_cluster_id_from_pvcs || true)"

if [[ -n "$PVC_CLUSTER_ID" ]]; then
    CLUSTER_ID="$PVC_CLUSTER_ID"
    if [[ -n "$CM_CLUSTER_ID" && "$CM_CLUSTER_ID" != "$CLUSTER_ID" ]]; then
        echo "Aligning ConfigMap cluster.id with PVC value: $CLUSTER_ID"
    else
        echo "Using cluster.id from PVC: $CLUSTER_ID"
    fi
    apply_cluster_id_configmap "$CLUSTER_ID"
elif [[ -n "$CM_CLUSTER_ID" ]]; then
    CLUSTER_ID="$CM_CLUSTER_ID"
    echo "Using cluster.id from ConfigMap: $CLUSTER_ID"
else
    CLUSTER_ID=$(cat /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9' | head -c 22)
    echo "Generated new Cluster ID: $CLUSTER_ID"
    apply_cluster_id_configmap "$CLUSTER_ID"
fi

echo ""
echo "Deploying Kafka StatefulSet..."

kubectl apply -f "$MANIFEST"

echo ""
echo "Waiting for Kafka pods to be ready..."
kubectl wait --for=condition=ready pod -l app=kafka -n $NAMESPACE --timeout=300s

echo ""
echo "================================================"
echo "  Kafka Installation Complete!                "
echo "================================================"
echo ""
echo "Kafka Details:"
echo "  - Replicas: 3 (combined broker + controller)"
echo "  - Namespace: $NAMESPACE"
echo "  - Service: kafka.$NAMESPACE.svc.cluster.local:9092"
echo "  - Storage: 20Gi per node"
echo "  - Memory: 2-4Gi per node"
echo "  - CPU: 1-2 cores per node"
echo ""
echo "Check status with:"
echo "  kubectl get pods -n $NAMESPACE -l app=kafka"
echo "  kubectl get pvc -n $NAMESPACE"
echo ""
echo "Connect to Kafka:"
echo "  kafka.$NAMESPACE.svc.cluster.local:9092"
echo ""
