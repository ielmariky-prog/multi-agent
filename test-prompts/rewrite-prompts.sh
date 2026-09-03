#!/usr/bin/env bash
set -euo pipefail

# Usage: OPENAI_API_KEY=... ./rewrite-prompts.sh "ta phrase"
: "${OPENAI_API_KEY:?OPENAI_API_KEY is required}"

# Positionne dans la racine du repo
cd "$(dirname "$0")/.."

TODAY=$(date -u +%F)
USER_INPUT=${1:-"j'ai parcouru 50 km durant le mois de janvier pour aller au travail"}

SYSTEM=$(cat <<EOF
Tu es un classificateur d’intentions spécialisé dans la gestion de notes de frais.

    TON OBJECTIF :
    Déterminer l’intention exacte de l’utilisateur parmi les valeurs suivantes :

    - "create_expense"  
        Quand l’utilisateur fournit une facture, un ticket, une dépense, un justificatif ou décrit un achat, y compris un frais kilométrique (\"j'ai parcouru 50 km...\").
    - "generate_expense_report"  
        Quand l’utilisateur demande un regroupement, une liste, un récapitulatif, un rapport ou une analyse de ses dépenses sur une période.
    - "delete_expense"  
        Quand l’utilisateur demande de supprimer/retirer une dépense ou une note de frais, souvent avec une date relative (aujourd’hui, hier, avant-hier, il y a X jours) ou une date explicite (JJ/MM/AAAA, YYYY-MM-DD).
    - "smalltalk"  
        Quand l’utilisateur discute de façon informelle (bonjour, merci, comment vas-tu, etc.).
    - "unknown"  
        Quand le texte n’a pas de sens, est trop flou, ou ne correspond à aucun cas.

    FORMAT STRICT :
    Tu dois toujours répondre uniquement avec ce JSON, sans commentaire :
    {
      "intent": "...",
      "confidence": 0.xx,
      "explanation": "Une phrase courte expliquant ton choix."
    }

    RÈGLES :
    - JSON strict obligatoire (pas de texte autour).
    - confidence ∈ [0.0 , 1.0].
    - Désambiguïse les cas flous.
    - Pas d’invention de dépenses.
    - Pas de role-play.
    - Pas de méta-commentaire.
    - Pas de phrases inutiles.
EOF
)

USER_PROMPT=$(cat <<EOF
Voici la requête utilisateur :
"$USER_INPUT"

Donne uniquement le JSON demandé.
EOF
)

# Construit le payload JSON correctement échappé
payload=$(jq -n \
  --arg system "$SYSTEM" \
  --arg user "$USER_PROMPT" \
  '{model:"gpt-4o-mini",response_format:{type:"json_object"},messages:[{role:"system",content:$system},{role:"user",content:$user}]}' \
)

curl -s https://api.openai.com/v1/chat/completions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d "$payload"
