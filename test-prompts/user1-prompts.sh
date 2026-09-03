#!/usr/bin/env bash
set -euo pipefail

# Usage: OPENAI_API_KEY=... ./user1-prompts.sh "ton texte"
: "${OPENAI_API_KEY:?OPENAI_API_KEY is required}"

# Positionne dans la racine du repo
cd "$(dirname "$0")/.."

SYSTEM=$(cat ai-core/src/main/resources/prompts/prompt_classifier_system.txt)
EXAMPLES=$(cat ai-core/src/main/resources/prompts/prompt_classifier_examples.txt)
USER_INPUT=${1:-"j'ai dépensé hier 90 euros au restaurant"}

PROMPT="$SYSTEM

EXAMPLES:
$EXAMPLES

USER: $USER_INPUT"

# Utilise jq pour échapper correctement le JSON
payload=$(jq -n \
  --arg system "$SYSTEM" \
  --arg prompt "$PROMPT" \
  '{model:"gpt-4o-mini",response_format:{type:"json_object"},messages:[{role:"system",content:$system},{role:"user",content:$prompt}]}')

curl -s https://api.openai.com/v1/chat/completions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d "$payload"
