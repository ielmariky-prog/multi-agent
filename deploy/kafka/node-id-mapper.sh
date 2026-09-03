#!/bin/bash

# This script maps pod names to Kafka node IDs
# Used internally by the StatefulSet

POD_NAME=${POD_NAME:-$HOSTNAME}

# Extract the ordinal from the pod name (e.g., kafka-0 -> 0)
if [[ $POD_NAME =~ -([0-9]+)$ ]]; then
    NODE_ID="${BASH_REMATCH[1]}"
    echo "$NODE_ID"
else
    echo "Error: Could not extract node ID from pod name: $POD_NAME" >&2
    exit 1
fi
