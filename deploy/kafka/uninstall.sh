#!/bin/bash

set -e

NAMESPACE="agent-system"

echo "================================================"
echo "  Kafka KRaft Uninstallation                  "
echo "================================================"

echo "Deleting Kafka StatefulSet and Services..."
kubectl delete statefulset kafka -n $NAMESPACE --ignore-not-found=true

echo "Deleting Kafka Services..."
kubectl delete service kafka kafka-headless -n $NAMESPACE --ignore-not-found=true

echo ""
read -p "Do you want to delete PersistentVolumeClaims (data will be lost)? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Deleting PersistentVolumeClaims..."
    kubectl delete pvc -l app=kafka -n $NAMESPACE --ignore-not-found=true
    echo "PVCs deleted"
else
    echo "PVCs kept (data preserved)"
fi

echo ""
echo "================================================"
echo "  Kafka Uninstallation Complete!              "
echo "================================================"
