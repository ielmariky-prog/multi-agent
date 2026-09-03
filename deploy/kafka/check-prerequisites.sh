#!/bin/bash

echo "================================================"
echo "  Kafka Deployment Prerequisites Check         "
echo "================================================"
echo ""

ERRORS=0
WARNINGS=0

# Function to check command
check_command() {
    local cmd=$1
    local required=$2
    
    if command -v $cmd &> /dev/null; then
        local version=$(eval "$cmd version 2>&1 | head -1" || echo "unknown")
        echo "✅ $cmd: installed"
        [ ! -z "$version" ] && echo "   Version: $version"
    else
        if [ "$required" = "true" ]; then
            echo "❌ $cmd: NOT FOUND (REQUIRED)"
            ((ERRORS++))
        else
            echo "⚠️  $cmd: NOT FOUND (optional)"
            ((WARNINGS++))
        fi
    fi
}

# Check required commands
echo "=== Required Tools ==="
check_command "kubectl" "true"
check_command "bash" "true"

echo ""
echo "=== Optional Tools ==="
check_command "helm" "false"
check_command "jq" "false"

echo ""
echo "=== Kubernetes Cluster ==="

if command -v kubectl &> /dev/null; then
    if kubectl cluster-info &> /dev/null; then
        echo "✅ Kubernetes cluster: accessible"
        
        # Check current context
        CONTEXT=$(kubectl config current-context 2>/dev/null)
        echo "   Context: $CONTEXT"
        
        # Check node count
        NODE_COUNT=$(kubectl get nodes --no-headers 2>/dev/null | wc -l | tr -d ' ')
        echo "   Nodes: $NODE_COUNT"
        
        if [ "$NODE_COUNT" -lt 3 ]; then
            echo "   ⚠️  Warning: Less than 3 nodes. Pod anti-affinity may not work optimally."
            ((WARNINGS++))
        fi
        
        # Check storage classes
        echo ""
        echo "=== Storage Classes ==="
        if kubectl get storageclass &> /dev/null; then
            DEFAULT_SC=$(kubectl get storageclass -o jsonpath='{.items[?(@.metadata.annotations.storageclass\.kubernetes\.io/is-default-class=="true")].metadata.name}' 2>/dev/null)
            if [ ! -z "$DEFAULT_SC" ]; then
                echo "✅ Default StorageClass: $DEFAULT_SC"
            else
                echo "⚠️  No default StorageClass found"
                echo "   Available StorageClasses:"
                kubectl get storageclass --no-headers 2>/dev/null | awk '{print "   - " $1}'
                ((WARNINGS++))
            fi
        fi
        
        # Check namespace
        echo ""
        echo "=== Namespace ==="
        if kubectl get namespace agent-system &> /dev/null; then
            echo "✅ Namespace 'agent-system': exists"
        else
            echo "ℹ️  Namespace 'agent-system': will be created during installation"
        fi
        
    else
        echo "❌ Kubernetes cluster: NOT accessible"
        echo "   Please configure kubectl to connect to your cluster"
        ((ERRORS++))
    fi
else
    echo "❌ Cannot check Kubernetes cluster (kubectl not found)"
    ((ERRORS++))
fi

echo ""
echo "=== Resource Requirements ==="
echo "Each Kafka pod requires:"
echo "  - CPU: 1-2 cores"
echo "  - Memory: 2-4Gi"
echo "  - Storage: 20Gi"
echo ""
echo "Total for 3 replicas:"
echo "  - CPU: 3-6 cores"
echo "  - Memory: 6-12Gi"
echo "  - Storage: 60Gi"

if command -v kubectl &> /dev/null && kubectl cluster-info &> /dev/null; then
    echo ""
    echo "=== Current Cluster Capacity ==="
    
    # Get total allocatable resources
    TOTAL_CPU=$(kubectl get nodes -o json 2>/dev/null | jq -r '[.items[].status.allocatable.cpu | gsub("m";"") | tonumber] | add' 2>/dev/null)
    TOTAL_MEM=$(kubectl get nodes -o json 2>/dev/null | jq -r '[.items[].status.allocatable.memory | gsub("Ki";"") | tonumber] | add / 1024 / 1024' 2>/dev/null)
    
    if [ ! -z "$TOTAL_CPU" ] && [ ! -z "$TOTAL_MEM" ]; then
        echo "  Total allocatable CPU: ${TOTAL_CPU}m"
        echo "  Total allocatable Memory: ${TOTAL_MEM}Gi"
        
        # Check if sufficient
        if (( $(echo "$TOTAL_CPU < 3000" | bc -l 2>/dev/null || echo "0") )); then
            echo "  ⚠️  Warning: May not have enough CPU for 3 Kafka replicas"
            ((WARNINGS++))
        fi
        
        if (( $(echo "$TOTAL_MEM < 6" | bc -l 2>/dev/null || echo "0") )); then
            echo "  ⚠️  Warning: May not have enough memory for 3 Kafka replicas"
            ((WARNINGS++))
        fi
    fi
fi

echo ""
echo "=== Permissions Check ==="
if command -v kubectl &> /dev/null && kubectl cluster-info &> /dev/null; then
    # Check if we can create resources
    if kubectl auth can-i create statefulset --namespace=agent-system &> /dev/null; then
        echo "✅ Can create StatefulSets"
    else
        echo "❌ Cannot create StatefulSets"
        ((ERRORS++))
    fi
    
    if kubectl auth can-i create service --namespace=agent-system &> /dev/null; then
        echo "✅ Can create Services"
    else
        echo "❌ Cannot create Services"
        ((ERRORS++))
    fi
    
    if kubectl auth can-i create persistentvolumeclaim --namespace=agent-system &> /dev/null; then
        echo "✅ Can create PersistentVolumeClaims"
    else
        echo "❌ Cannot create PersistentVolumeClaims"
        ((ERRORS++))
    fi
fi

echo ""
echo "================================================"
echo "  Check Summary                                "
echo "================================================"

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo "✅ All checks passed! Ready to install Kafka."
    echo ""
    echo "Next steps:"
    echo "  1. Run: ./install.sh"
    echo "  2. Wait for pods to be ready (~2-3 minutes)"
    echo "  3. Test with: ./test-kafka.sh"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo "⚠️  $WARNINGS warning(s) found. You can proceed but review warnings above."
    echo ""
    echo "To proceed anyway:"
    echo "  ./install.sh"
    exit 0
else
    echo "❌ $ERRORS error(s) and $WARNINGS warning(s) found."
    echo ""
    echo "Please fix the errors above before installing Kafka."
    exit 1
fi
