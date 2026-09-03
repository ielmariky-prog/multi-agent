#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

# ─────────────────────────────────────────────────────────────
# Couleurs pour les logs
# ─────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
section() { echo -e "\n${GREEN}══════════════════════════════════════${NC}"; echo -e "${GREEN} $1${NC}"; echo -e "${GREEN}══════════════════════════════════════${NC}"; }

# ─────────────────────────────────────────────────────────────
# Vérifications préalables
# ─────────────────────────────────────────────────────────────
section "1. Vérifications"
command -v kubectl >/dev/null || { echo -e "${RED}kubectl non trouvé${NC}"; exit 1; }
command -v docker  >/dev/null || { echo -e "${RED}docker non trouvé${NC}"; exit 1; }
command -v mvn     >/dev/null || { echo -e "${RED}mvn non trouvé${NC}"; exit 1; }

kubectl cluster-info --request-timeout=5s >/dev/null || { echo -e "${RED}Cluster Kubernetes inaccessible. Lance Docker Desktop avec Kubernetes activé.${NC}"; exit 1; }
info "Cluster Kubernetes OK"

# Vérification des clés dans le secret
ANTHROPIC_KEY=$(grep 'ANTHROPIC_API_KEY' "$ROOT/ai-core/deploy/k8s/secret.yaml" | awk '{print $2}' | tr -d '"')
VOYAGE_KEY=$(grep 'VOYAGE_API_KEY' "$ROOT/ai-core/deploy/k8s/secret.yaml" | awk '{print $2}' | tr -d '"')
if [[ "$ANTHROPIC_KEY" == "CHANGE_ME" || -z "$ANTHROPIC_KEY" ]]; then
  echo -e "${RED}[ERREUR] Tu dois mettre ta clé Anthropic dans ai-core/deploy/k8s/secret.yaml${NC}"
  echo -e "${YELLOW}  → Remplace CHANGE_ME par ta clé sk-ant-...${NC}"
  exit 1
fi
if [[ "$VOYAGE_KEY" == "CHANGE_ME" || -z "$VOYAGE_KEY" ]]; then
  echo -e "${RED}[ERREUR] Tu dois mettre ta clé Voyage AI dans ai-core/deploy/k8s/secret.yaml${NC}"
  echo -e "${YELLOW}  → Remplace CHANGE_ME par ta clé pa-... (voyage ai.com)${NC}"
  exit 1
fi
info "Clés Anthropic + Voyage AI présentes"

# ─────────────────────────────────────────────────────────────
# Compilation Maven
# ─────────────────────────────────────────────────────────────
section "2. Compilation Maven (mvn package)"
cd "$ROOT"
mvn clean package -DskipTests -q
info "Compilation OK"

# ─────────────────────────────────────────────────────────────
# Build des images Docker locales
# ─────────────────────────────────────────────────────────────
section "3. Build des images Docker"
build_image() {
  info "Build $1:local..."
  docker build -t "$1:local" "$ROOT/$1" -q
  info "  ✅ $1:local"
}
build_image ai-core
build_image intent-agent
build_image reasoning-agent
build_image reassign-agent
build_image audit-agent
build_image mcp-server

# ─────────────────────────────────────────────────────────────
# Déploiement Kubernetes
# ─────────────────────────────────────────────────────────────
section "4. Namespace"
kubectl apply -f "$ROOT/deploy/k8s/00-namespace.yaml"
info "Namespace multi-agent créé"

section "5. Secret OpenAI"
kubectl apply -f "$ROOT/ai-core/deploy/k8s/secret.yaml"
info "Secret openai-secret appliqué"

section "6. Kafka"
kubectl apply -f "$ROOT/deploy/k8s/01-kafka.yaml"
info "Attente que Kafka soit prêt..."
kubectl rollout status statefulset/kafka -n multi-agent --timeout=180s
info "Kafka prêt ✅"

section "7. Topics Kafka"
# Supprime le job si déjà exécuté précédemment
kubectl delete job kafka-topics-init -n multi-agent --ignore-not-found=true
kubectl apply -f "$ROOT/deploy/k8s/02-kafka-topics.yaml"
info "Attente de la création des topics..."
kubectl wait --for=condition=complete job/kafka-topics-init -n multi-agent --timeout=120s
info "Topics créés ✅"

section "8. Weaviate"
kubectl apply -f "$ROOT/deploy/k8s/03-weaviate.yaml"
info "Attente que Weaviate soit prêt..."
kubectl rollout status deployment/weaviate -n multi-agent --timeout=120s
info "Weaviate prêt ✅"

section "9. AI-Core"
kubectl apply -f "$ROOT/ai-core/deploy/k8s/configMap.yaml"
kubectl apply -f "$ROOT/ai-core/deploy/k8s/deployment.yaml"
info "Attente qu'ai-core soit prêt..."
kubectl rollout status deployment/ai-core -n multi-agent --timeout=180s
info "AI-Core prêt ✅"

section "10. Agents"
for agent in intent-agent reasoning-agent reassign-agent audit-agent; do
  info "Déploiement $agent..."
  kubectl apply -f "$ROOT/$agent/deploy/k8s/configMap.yaml"
  kubectl apply -f "$ROOT/$agent/deploy/k8s/deployment.yaml"
done
info "Attente que tous les agents soient prêts..."
for agent in intent-agent reasoning-agent reassign-agent audit-agent; do
  kubectl rollout status deployment/$agent -n multi-agent --timeout=120s
  info "  ✅ $agent prêt"
done

section "11. MCP Governance Server"
kubectl apply -f "$ROOT/mcp-server/deploy/k8s/configMap.yaml"
kubectl apply -f "$ROOT/mcp-server/deploy/k8s/secret.yaml"
kubectl apply -f "$ROOT/mcp-server/deploy/k8s/deployment.yaml"
info "Attente que le serveur MCP soit prêt..."
kubectl rollout status deployment/mcp-server -n multi-agent --timeout=120s
info "MCP Server prêt ✅"

# ─────────────────────────────────────────────────────────────
# Résumé final
# ─────────────────────────────────────────────────────────────
section "✅ Déploiement terminé !"
echo ""
kubectl get pods -n multi-agent
echo ""
info "Accès à AI-Core depuis ta machine :"
info "  kubectl port-forward svc/ai-core 8081:8081 -n multi-agent"
info "Accès au MCP Server depuis ta machine :"
info "  kubectl port-forward svc/mcp-server 8082:8082 -n multi-agent"
info "Accès à Weaviate depuis ta machine :"
info "  kubectl port-forward svc/weaviate 8080:8080 -n multi-agent"
echo ""
info "Logs d'un service :"
info "  kubectl logs -f deployment/ai-core -n multi-agent"
info "  kubectl logs -f deployment/mcp-server -n multi-agent"
info "  kubectl logs -f deployment/intent-agent -n multi-agent"
